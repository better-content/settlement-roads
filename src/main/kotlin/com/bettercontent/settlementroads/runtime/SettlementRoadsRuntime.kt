package com.bettercontent.settlementroads.runtime

import com.bettercontent.settlementroads.config.SettlementRoadsConfig
import com.bettercontent.settlementroads.data.PlannedRoadNetwork
import com.bettercontent.settlementroads.data.RoadNetworkState
import com.bettercontent.settlementroads.data.SettlementRoadsSavedData
import com.bettercontent.settlementroads.planner.PlannerConfig
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.level.LevelEvent
import java.util.concurrent.ConcurrentLinkedQueue

data class RuntimeStatus(
    val knownStructures: Int,
    val activeStructures: Int,
    val connections: Int,
    val appliedSegments: Int
)

object SettlementRoadsRuntime {
    private val loadedChunks = mutableMapOf<Level, MutableSet<Long>>()
    private val dirtyLevels = mutableSetOf<Level>()
    private val pendingMainThreadWork = ConcurrentLinkedQueue<Pair<ServerLevel, () -> Unit>>()
    private val lastRebuildGameTime = mutableMapOf<Level, Long>()
    private val lastPlacementGameTime = mutableMapOf<Level, Long>()
    private const val PLACEMENT_INTERVAL_TICKS = 5L
    private const val OBSERVED_CHUNK_REFRESH_INTERVAL_TICKS = 40L
    private const val PLAYER_CHUNK_RADIUS = 8
    private val config: PlannerConfig
        get() = SettlementRoadsConfig.plannerConfig()

    fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level != level.server.overworld()) {
            return
        }
        enqueue(level) {
            loadedChunks.getOrPut(level) { mutableSetOf() }
        }
    }

    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        enqueue(level) {
            loadedChunks.remove(level)
            dirtyLevels.remove(level)
            lastRebuildGameTime.remove(level)
            lastPlacementGameTime.remove(level)
        }
    }

    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level != level.server.overworld()) {
            return
        }

        val chunkKey = event.chunk.pos.toLong()
        enqueue(level) {
            if (!isNearAnyPlayer(level, event.chunk.pos)) {
                return@enqueue
            }
            if (loadedChunks.getOrPut(level) { mutableSetOf() }.add(chunkKey)) {
                markDirtyNow(level)
            }
        }
    }

    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk
        val chunkKey = chunk.pos.toLong()
        enqueue(level) {
            if (loadedChunks[level]?.remove(chunkKey) == true) {
                markDirtyNow(level)
            }
        }
    }

    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        val level = event.level as? ServerLevel ?: return
        if (event.phase != TickEvent.Phase.END || level != level.server.overworld()) {
            return
        }

        drainPendingWork(level)
        refreshObservedLoadedChunks(level)
        val hasPlayers = level.players().isNotEmpty()
        val shouldPeriodicRefresh = hasPlayers &&
            loadedChunks[level].orEmpty().isNotEmpty() &&
            (level.gameTime % 100L == 0L) &&
            SettlementRoadsSavedData.get(level).state.worldNetwork.structures.isEmpty()
        val plannerConfig = config
        val rebuildCooldownElapsed = level.gameTime - (lastRebuildGameTime[level] ?: Long.MIN_VALUE) >= plannerConfig.minTicksBetweenRebuilds

        if ((dirtyLevels.contains(level) && rebuildCooldownElapsed) || shouldPeriodicRefresh) {
            dirtyLevels.remove(level)
            rebuildFromLoadedChunks(level)
            lastRebuildGameTime[level] = level.gameTime
        }
        if (hasPlayers && level.gameTime - (lastPlacementGameTime[level] ?: Long.MIN_VALUE) >= PLACEMENT_INTERVAL_TICKS) {
            placeAvailable(level)
            lastPlacementGameTime[level] = level.gameTime
        }
        if (hasPlayers) RoadJourneyTracker.tick(level, SettlementRoadsSavedData.get(level).state.worldNetwork)
    }

    fun rebuildFromLoadedChunks(level: ServerLevel): RuntimeStatus {
        val loadedChunkKeys = loadedChunks.getOrPut(level) { mutableSetOf() }
        val savedData = SettlementRoadsSavedData.get(level)
        val plannerConfig = config
        val scan = WorldStructureScanner.scanLoadedChunks(level, loadedChunkKeys, savedData.state.worldStructures, plannerConfig)
        val worldNetwork = WorldNetworkPlanner.plan(level, scan.activeStructures, plannerConfig)
        val retainedAppliedSegments = savedData.state.worldNetwork.appliedSegments.intersect(worldNetwork.segmentIds())

        savedData.update {
            it.copy(
                worldStructures = scan.discoveredStructures,
                worldNetwork = worldNetwork.copy(appliedSegments = retainedAppliedSegments)
            )
        }

        return status(level)
    }

    fun placeAvailable(level: ServerLevel): PlacementResult {
        val loadedChunkKeys = loadedChunks.getOrPut(level) { mutableSetOf() }
        val savedData = SettlementRoadsSavedData.get(level)
        val state = savedData.state
        if (state.worldNetwork.structures.isEmpty()) {
            return PlacementResult(0, 0, emptySet())
        }

        val placement = WorldNetworkPlacer.placeAvailable(level, state.worldNetwork, loadedChunkKeys, config)
        if (placement.appliedSegments.isNotEmpty()) {
            savedData.update {
                it.copy(
                    worldNetwork = it.worldNetwork.copy(
                        appliedSegments = it.worldNetwork.appliedSegments + placement.appliedSegments
                    )
                )
            }
        }

        return placement
    }

    fun clear(level: ServerLevel) {
        SettlementRoadsSavedData.get(level).update { current ->
            current.copy(
                worldStructures = emptyList(),
                worldNetwork = PlannedRoadNetwork()
            )
        }
    }

    fun markDirty(level: ServerLevel) {
        enqueue(level) {
            markDirtyNow(level)
        }
    }

    fun status(level: ServerLevel): RuntimeStatus {
        val state = SettlementRoadsSavedData.get(level).state
        val loaded = loadedChunks[level].orEmpty()
        val activeStructures = state.worldStructures.count { structure ->
            val chunkX = structure.sourceChunkX
            val chunkZ = structure.sourceChunkZ
            chunkX == null || chunkZ == null || ChunkPos.asLong(chunkX, chunkZ) in loaded
        }

        return RuntimeStatus(
            knownStructures = state.worldStructures.size,
            activeStructures = activeStructures,
            connections = state.worldNetwork.clusters.sumOf { cluster -> cluster.connections.size },
            appliedSegments = state.worldNetwork.appliedSegments.size
        )
    }

    private fun enqueue(level: ServerLevel, task: () -> Unit) {
        pendingMainThreadWork += level to task
    }

    private fun drainPendingWork(level: ServerLevel) {
        while (true) {
            val next = pendingMainThreadWork.poll() ?: break
            if (next.first === level) {
                next.second.invoke()
            } else {
                pendingMainThreadWork += next
                break
            }
        }
    }

    private fun refreshObservedLoadedChunks(level: ServerLevel) {
        if (level.gameTime % OBSERVED_CHUNK_REFRESH_INTERVAL_TICKS != 0L) {
            return
        }

        val previous = loadedChunks.getOrPut(level) { mutableSetOf() }
        val refreshed = observedLoadedChunks(level)
        if (refreshed != previous) {
            loadedChunks[level] = refreshed.toMutableSet()
            markDirtyNow(level)
        }
    }

    private fun markDirtyNow(level: ServerLevel) {
        dirtyLevels += level
    }

    private fun observedLoadedChunks(level: ServerLevel): Set<Long> {
        val anchors = level.players().map(ServerPlayer::chunkPosition)
        return buildSet {
            for (anchor in anchors) {
                for (chunkX in (anchor.x - PLAYER_CHUNK_RADIUS)..(anchor.x + PLAYER_CHUNK_RADIUS)) {
                    for (chunkZ in (anchor.z - PLAYER_CHUNK_RADIUS)..(anchor.z + PLAYER_CHUNK_RADIUS)) {
                        if (level.chunkSource.getChunkNow(chunkX, chunkZ) != null) {
                            add(ChunkPos.asLong(chunkX, chunkZ))
                        }
                    }
                }
            }
        }
    }

    private fun isNearAnyPlayer(level: ServerLevel, chunk: ChunkPos): Boolean =
        level.players().any { player ->
            val playerChunk = player.chunkPosition()
            kotlin.math.abs(playerChunk.x - chunk.x) <= PLAYER_CHUNK_RADIUS &&
                kotlin.math.abs(playerChunk.z - chunk.z) <= PLAYER_CHUNK_RADIUS
        }

    private fun PlannedRoadNetwork.segmentIds(): Set<String> =
        buildSet {
            rings.forEach { ring -> add(WorldNetworkPlacer.ringSegmentId(ring.structureId)) }
            clusters.forEach { cluster ->
                cluster.connections.forEach { connection ->
                    connection.segments.indices.forEach { index ->
                        add(WorldNetworkPlacer.connectionSegmentId(connection.id, index))
                    }
                }
            }
        }
}
