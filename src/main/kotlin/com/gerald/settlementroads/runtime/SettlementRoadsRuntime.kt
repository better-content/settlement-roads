package com.gerald.settlementroads.runtime

import com.gerald.settlementroads.SettlementRoadsMod
import com.gerald.settlementroads.data.PlannedRoadNetwork
import com.gerald.settlementroads.data.RoadNetworkState
import com.gerald.settlementroads.data.SettlementRoadsSavedData
import com.gerald.settlementroads.planner.PlannerConfig
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
    private val plannerConfig = PlannerConfig()
    private val loadedChunks = mutableMapOf<Level, MutableSet<Long>>()
    private val dirtyLevels = mutableSetOf<Level>()
    private val pendingMainThreadWork = ConcurrentLinkedQueue<Pair<ServerLevel, () -> Unit>>()
    private val lastRebuildGameTime = mutableMapOf<Level, Long>()

    fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level != level.server.overworld()) {
            return
        }
        enqueue(level) {
            loadedChunks.getOrPut(level) { mutableSetOf() }
            dirtyLevels += level
        }
    }

    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        enqueue(level) {
            loadedChunks.remove(level)
            dirtyLevels.remove(level)
            lastRebuildGameTime.remove(level)
        }
    }

    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level != level.server.overworld()) {
            return
        }

        val chunkKey = event.chunk.pos.toLong()
        enqueue(level) {
            loadedChunks.getOrPut(level) { mutableSetOf() } += chunkKey
            dirtyLevels += level
        }
    }

    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk
        val chunkKey = chunk.pos.toLong()
        enqueue(level) {
            loadedChunks[level]?.remove(chunkKey)
            dirtyLevels += level
        }
    }

    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        val level = event.level as? ServerLevel ?: return
        if (event.phase != TickEvent.Phase.END || level != level.server.overworld()) {
            return
        }

        drainPendingWork(level)
        refreshObservedLoadedChunks(level)
        val shouldPeriodicRefresh = loadedChunks[level].orEmpty().isNotEmpty() &&
            (level.gameTime % 100L == 0L) &&
            SettlementRoadsSavedData.get(level).state.worldNetwork.structures.isEmpty()
        val rebuildCooldownElapsed = level.gameTime - (lastRebuildGameTime[level] ?: Long.MIN_VALUE) >= plannerConfig.minTicksBetweenRebuilds

        if ((dirtyLevels.contains(level) && rebuildCooldownElapsed) || shouldPeriodicRefresh) {
            dirtyLevels.remove(level)
            rebuildFromLoadedChunks(level)
            lastRebuildGameTime[level] = level.gameTime
        }
        placeAvailable(level)
    }

    fun rebuildFromLoadedChunks(level: ServerLevel): RuntimeStatus {
        val loadedChunkKeys = loadedChunks.getOrPut(level) { mutableSetOf() }
        val savedData = SettlementRoadsSavedData.get(level)
        val scan = WorldStructureScanner.scanLoadedChunks(level, loadedChunkKeys, savedData.state.worldStructures, plannerConfig)
        val worldNetwork = WorldNetworkPlanner.plan(level, scan.activeStructures, plannerConfig)
        val retainedAppliedSegments = savedData.state.worldNetwork.appliedSegments.intersect(worldNetwork.segmentIds())

        savedData.update {
            it.copy(
                worldStructures = scan.discoveredStructures,
                worldNetwork = worldNetwork.copy(appliedSegments = retainedAppliedSegments)
            )
        }

        SettlementRoadsMod.LOGGER.info(
            "Rebuilt world network: {} discovered structures, {} active structures, {} connections",
            scan.discoveredStructures.size,
            scan.activeStructures.size,
            worldNetwork.clusters.sumOf { cluster -> cluster.connections.size }
        )

        return status(level)
    }

    fun placeAvailable(level: ServerLevel): PlacementResult {
        val loadedChunkKeys = loadedChunks.getOrPut(level) { mutableSetOf() }
        val savedData = SettlementRoadsSavedData.get(level)
        val state = savedData.state
        if (state.worldNetwork.structures.isEmpty()) {
            return PlacementResult(0, emptySet())
        }

        val placement = WorldNetworkPlacer.placeAvailable(level, state.worldNetwork, loadedChunkKeys, plannerConfig)
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
            dirtyLevels += level
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
        if (level.gameTime % 20L != 0L) {
            return
        }

        val previous = loadedChunks.getOrPut(level) { mutableSetOf() }
        val refreshed = observedLoadedChunks(level)
        if (refreshed != previous) {
            loadedChunks[level] = refreshed.toMutableSet()
            dirtyLevels += level
        }
    }

    private fun observedLoadedChunks(level: ServerLevel): Set<Long> {
        val anchors = level.players().map(ServerPlayer::chunkPosition).ifEmpty { listOf(ChunkPos(level.sharedSpawnPos)) }
        return buildSet {
            for (anchor in anchors) {
                for (chunkX in (anchor.x - 8)..(anchor.x + 8)) {
                    for (chunkZ in (anchor.z - 8)..(anchor.z + 8)) {
                        if (level.chunkSource.getChunkNow(chunkX, chunkZ) != null) {
                            add(ChunkPos.asLong(chunkX, chunkZ))
                        }
                    }
                }
            }
        }
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
