package com.bettercontent.settlementroads.runtime

import com.bettercontent.settlementroads.data.PlannedRoadNetwork
import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.model.PathSegment
import com.bettercontent.settlementroads.planner.palette.SurfacePalette
import com.bettercontent.settlementroads.planner.palette.SurfacePaletteSelector
import com.bettercontent.settlementroads.planner.placement.SegmentIdCodec
import com.bettercontent.settlementroads.tag.SettlementRoadsTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

data class PlacementResult(
    val placedBlocks: Int,
    val placedWallBlocks: Int,
    val appliedSegments: Set<String>
)

object WorldNetworkPlacer {
    fun placeAvailable(
        level: ServerLevel,
        network: PlannedRoadNetwork,
        loadedChunkKeys: Set<Long>,
        config: PlannerConfig = PlannerConfig()
    ): PlacementResult {
        var placedBlocks = 0
        var placedWallBlocks = 0
        val appliedSegments = mutableSetOf<String>()
        val chunkStampsBySegment = network.chunkStamps.groupBy { it.segmentId }
        var remainingSegments = config.maxSegmentsPlacedPerTick

        for (ring in network.rings) {
            if (remainingSegments <= 0) {
                break
            }

            val segmentId = ringSegmentId(ring.structureId)
            if (segmentId in network.appliedSegments) {
                continue
            }

            val ringBlocks = ring.perimeter.dropLast(1)
            if (ringBlocks.isEmpty()) {
                continue
            }
            if (!roadFootprintLoaded(level, ringBlocks, loadedChunkKeys)) {
                continue
            }

            val roadPlacement = placeRoad(level, ringBlocks, segmentId, config)
            placedBlocks += roadPlacement.placedBlocks
            placedWallBlocks += roadPlacement.placedWallBlocks
            appliedSegments += segmentId
            remainingSegments--
        }

        for (cluster in network.clusters) {
            if (remainingSegments <= 0) {
                break
            }

            for (connection in cluster.connections) {
                if (remainingSegments <= 0) {
                    break
                }

                connection.segments.forEachIndexed { index, segment ->
                    if (remainingSegments <= 0) {
                        return@forEachIndexed
                    }

                    val segmentId = connectionSegmentId(connection.id, index)
                    if (segmentId in network.appliedSegments) {
                        return@forEachIndexed
                    }

                    val requiredChunks = chunkStampsBySegment[segmentId].orEmpty()
                    if (requiredChunks.any { ChunkPos.asLong(it.chunkX, it.chunkZ) !in loadedChunkKeys }) {
                        return@forEachIndexed
                    }
                    if (segment is PathSegment.Ground && !roadFootprintLoaded(level, segment.blocks, loadedChunkKeys)) {
                        return@forEachIndexed
                    }

                    placedBlocks += when (segment) {
                        is PathSegment.Bridge -> placeBridge(level, segment)
                        is PathSegment.Ground -> {
                            val roadPlacement = placeRoad(level, segment.blocks, segmentId, config)
                            placedWallBlocks += roadPlacement.placedWallBlocks
                            roadPlacement.placedBlocks
                        }
                    }
                    appliedSegments += segmentId
                    remainingSegments--
                }
            }
        }

        return PlacementResult(placedBlocks, placedWallBlocks, appliedSegments)
    }

    fun ringSegmentId(structureId: String): String =
        SegmentIdCodec.ring(structureId)

    fun connectionSegmentId(connectionId: String, index: Int): String =
        SegmentIdCodec.connection(connectionId, index)

    private data class RoadPlacement(val placedBlocks: Int, val placedWallBlocks: Int)

    private fun placeRoad(
        level: ServerLevel,
        centerline: List<BlockPos>,
        seedKey: String,
        config: PlannerConfig
    ): RoadPlacement {
        if (centerline.isEmpty()) {
            return RoadPlacement(0, 0)
        }

        val snappedCenterline = centerline.map { snapToSurface(level, it) }
        val seed = seedKey.hashCode().toLong()
        var placedBlocks = 0
        val roadPositions = buildList {
            for ((index, pos) in snappedCenterline.withIndex()) {
                add(pos to true)
                for ((offsetX, offsetZ) in lateralOffsets(snappedCenterline, index)) {
                    add(snapToSurface(level, pos.offset(offsetX, 0, offsetZ)) to false)
                }
            }
        }
        val centerlinePositions = roadPositions.filter { it.second }.mapTo(linkedSetOf()) { it.first }
        val footprint = roadPositions.mapTo(linkedSetOf()) { it.first }
        val detailPositions = SurfacePaletteSelector.chooseSparseWeatheringDetail(
            pathFootprint = footprint,
            centerline = centerlinePositions,
            seed = seed,
            detailRate = config.coarseDirtEdgeRate
        )

        for ((pos, _) in roadPositions.distinctBy { it.first }) {
            placedBlocks += placeRoadBlockIfRoadable(level, pos, pos in detailPositions)
        }

        return RoadPlacement(placedBlocks, 0)
    }

    private fun snapToSurface(level: ServerLevel, pos: BlockPos): BlockPos {
        return if (isLoaded(level, pos)) SurfaceSampler.groundPos(level, pos.x, pos.z) else pos
    }

    private fun placeBridge(level: ServerLevel, segment: PathSegment.Bridge): Int {
        var placed = 0
        for ((index, pos) in segment.blocks.withIndex()) {
            val lateralOffsets = lateralOffsets(segment.blocks, index)
            placed += placeBridgeBlockIfSafe(level, pos, Blocks.STONE_BRICKS)
            for ((offsetX, offsetZ) in lateralOffsets) {
                placed += placeBridgeBlockIfSafe(level, pos.offset(offsetX, 0, offsetZ), Blocks.STONE_BRICKS)
            }

            val wallOffsets = wallOffsets(lateralOffsets)
            for ((offsetX, offsetZ) in wallOffsets) {
                placed += placeBridgeBlockIfSafe(level, pos.offset(offsetX, 1, offsetZ), Blocks.STONE_BRICK_WALL)
            }
        }

        for (support in segment.supports) {
            placed += placeVerticalColumn(level, support.x, support.fromY, support.toY, support.z)
            if (support.baseWidth > 1) {
                val radius = support.baseWidth - 1
                for (offsetX in -radius..radius) {
                    for (offsetZ in -radius..radius) {
                        placed += placeBridgeBlockIfSafe(level, BlockPos(support.x + offsetX, support.toY, support.z + offsetZ), Blocks.COBBLESTONE)
                    }
                }
            }
        }

        return placed
    }

    private fun placeRoadBlockIfRoadable(
        level: ServerLevel,
        pos: BlockPos,
        coarseDetail: Boolean
    ): Int {
        if (!isRoadableGround(level, pos)) {
            return 0
        }

        return placeIfChanged(level, pos, roadBlock(level, pos, coarseDetail))
    }

    private fun roadBlock(level: ServerLevel, pos: BlockPos, coarseDetail: Boolean): Block {
        if (coarseDetail) return Blocks.COARSE_DIRT
        val biome = level.getBiome(pos)
        val isGrassy = biome.`is`(SettlementRoadsTags.Biomes.GRASSY_BIOMES) ||
            !biome.`is`(SettlementRoadsTags.Biomes.NON_GRASSY_BIOMES)
        return when (SurfacePaletteSelector.choose(isGrassy)) {
            SurfacePalette.GRASSY -> Blocks.DIRT_PATH
            SurfacePalette.NON_GRASSY -> Blocks.GRAVEL
        }
    }

    private fun isRoadableGround(level: ServerLevel, pos: BlockPos): Boolean {
        if (!isLoaded(level, pos) || level.getBlockEntity(pos) != null) return false
        val state = level.getBlockState(pos)
        // This is an intentionally narrow natural surface list. A material tag cannot
        // establish whether a player placed a block, but excludes ordinary construction.
        return state.fluidState.isEmpty && state.block in NATURAL_GROUND
    }

    private fun wallOffsets(roadOffsets: List<Pair<Int, Int>>): List<Pair<Int, Int>> =
        if (roadOffsets.all { it.first == 0 }) {
            listOf(0 to -2, 0 to 2)
        } else {
            listOf(-2 to 0, 2 to 0)
        }

    private fun lateralOffsets(path: List<BlockPos>, index: Int): List<Pair<Int, Int>> {
        val current = path[index]
        val next = path.getOrNull(index + 1) ?: path.getOrNull(index - 1) ?: current
        return if (kotlin.math.abs(next.x - current.x) >= kotlin.math.abs(next.z - current.z)) {
            listOf(0 to -1, 0 to 1)
        } else {
            listOf(-1 to 0, 1 to 0)
        }
    }

    private fun roadFootprintLoaded(level: ServerLevel, path: List<BlockPos>, loadedChunkKeys: Set<Long>): Boolean =
        path.indices.all { index ->
            val point = path[index]
            (listOf(point) + lateralOffsets(path, index).map { (dx, dz) -> point.offset(dx, 0, dz) })
                .all { ChunkPos.asLong(it.x shr 4, it.z shr 4) in loadedChunkKeys && isLoaded(level, it) }
        }

    private fun placeVerticalColumn(level: ServerLevel, x: Int, fromY: Int, toY: Int, z: Int): Int {
        var placed = 0
        val top = maxOf(fromY, toY)
        val bottom = minOf(fromY, toY)
        for (y in bottom..top) {
            placed += placeBridgeBlockIfSafe(level, BlockPos(x, y, z), Blocks.COBBLESTONE)
        }
        return placed
    }

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, block: Block): Int =
        placeIfChanged(level, pos, block.defaultBlockState())

    private fun placeBridgeBlockIfSafe(level: ServerLevel, pos: BlockPos, block: Block): Int {
        if (!isLoaded(level, pos) || level.getBlockEntity(pos) != null) return 0
        val expected = level.getBlockState(pos)
        if (!expected.isAir && expected.fluidState.isEmpty && expected.block !in NATURAL_GROUND) return 0
        return placeIfChanged(level, pos, block)
    }

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, state: BlockState): Int {
        if (!isLoaded(level, pos)) return 0
        val expected = level.getBlockState(pos)
        if (expected == state || level.getBlockEntity(pos) != null) {
            return 0
        }
        // Recheck the expected state before mutation, so an intervening edit wins.
        if (level.getBlockState(pos) != expected) return 0
        level.setBlockAndUpdate(pos, state)
        return 1
    }

    private val NATURAL_GROUND = setOf(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
        Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.CLAY,
        Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.STONE,
        Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF,
        Blocks.DEEPSLATE, Blocks.SNOW_BLOCK
    )

    private fun isLoaded(level: ServerLevel, pos: BlockPos): Boolean =
        level.chunkSource.hasChunk(pos.x shr 4, pos.z shr 4)
}
