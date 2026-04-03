package com.gerald.settlementroads.runtime

import com.gerald.settlementroads.data.PlannedRoadNetwork
import com.gerald.settlementroads.planner.PlannerConfig
import com.gerald.settlementroads.planner.model.PathSegment
import com.gerald.settlementroads.planner.placement.SegmentIdCodec
import com.gerald.settlementroads.planner.palette.SurfacePaletteSelector
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

data class PlacementResult(
    val placedBlocks: Int,
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
            if (ringBlocks.any { ChunkPos.asLong(it.x shr 4, it.z shr 4) !in loadedChunkKeys }) {
                continue
            }

            placedBlocks += placeRoad(level, ringBlocks, segmentId, config)
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

                    placedBlocks += when (segment) {
                        is PathSegment.Bridge -> placeBridge(level, segment)
                        is PathSegment.Ground -> placeRoad(level, segment.blocks, segmentId, config)
                    }
                    appliedSegments += segmentId
                    remainingSegments--
                }
            }
        }

        return PlacementResult(placedBlocks, appliedSegments)
    }

    fun ringSegmentId(structureId: String): String =
        SegmentIdCodec.ring(structureId)

    fun connectionSegmentId(connectionId: String, index: Int): String =
        SegmentIdCodec.connection(connectionId, index)

    private fun placeRoad(level: ServerLevel, centerline: List<BlockPos>, seedKey: String, config: PlannerConfig): Int {
        if (centerline.isEmpty()) {
            return 0
        }

        val snappedCenterline = centerline.map { snapToSurface(level, it) }
        val centerSet = snappedCenterline.toSet()
        val footprint = mutableSetOf<BlockPos>()
        var placed = 0

        for ((index, pos) in snappedCenterline.withIndex()) {
            val lateralOffsets = lateralOffsets(snappedCenterline, index)
            footprint += pos
            placed += placeIfChanged(level, pos, chooseRoadBlock(level, pos))
            for ((offsetX, offsetZ) in lateralOffsets) {
                val edge = snapToSurface(level, pos.offset(offsetX, 0, offsetZ))
                footprint += edge
                placed += placeIfChanged(level, edge, chooseRoadBlock(level, edge))
            }
        }

        val detail = SurfacePaletteSelector.chooseCoarseDirtDetail(
            pathFootprint = footprint,
            centerline = centerSet,
            seed = seedKey.hashCode().toLong(),
            detailRate = config.coarseDirtRate
        )
        for (pos in detail) {
            if (shouldUseDirtPath(level, pos)) {
                placed += placeIfChanged(level, pos, Blocks.COARSE_DIRT)
            }
        }

        return placed
    }

    private fun snapToSurface(level: ServerLevel, pos: BlockPos): BlockPos {
        return SurfaceSampler.groundPos(level, pos.x, pos.z)
    }

    private fun placeBridge(level: ServerLevel, segment: PathSegment.Bridge): Int {
        var placed = 0
        for ((index, pos) in segment.blocks.withIndex()) {
            val lateralOffsets = lateralOffsets(segment.blocks, index)
            placed += placeIfChanged(level, pos, Blocks.STONE_BRICKS)
            for ((offsetX, offsetZ) in lateralOffsets) {
                placed += placeIfChanged(level, pos.offset(offsetX, 0, offsetZ), Blocks.STONE_BRICKS)
            }

            val wallOffsets = wallOffsets(lateralOffsets)
            for ((offsetX, offsetZ) in wallOffsets) {
                placed += placeIfChanged(level, pos.offset(offsetX, 1, offsetZ), Blocks.STONE_BRICK_WALL)
            }
        }

        for (support in segment.supports) {
            placed += placeVerticalColumn(level, support.x, support.fromY, support.toY, support.z)
            if (support.baseWidth > 1) {
                val radius = support.baseWidth - 1
                for (offsetX in -radius..radius) {
                    for (offsetZ in -radius..radius) {
                        placed += placeIfChanged(level, BlockPos(support.x + offsetX, support.toY, support.z + offsetZ), Blocks.COBBLESTONE)
                    }
                }
            }
        }

        return placed
    }

    private fun chooseRoadBlock(level: ServerLevel, pos: BlockPos): Block =
        if (shouldUseDirtPath(level, pos)) Blocks.DIRT_PATH else Blocks.COBBLESTONE

    private fun shouldUseDirtPath(level: ServerLevel, pos: BlockPos): Boolean =
        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).path.contains("grass")

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

    private fun placeVerticalColumn(level: ServerLevel, x: Int, fromY: Int, toY: Int, z: Int): Int {
        var placed = 0
        val top = maxOf(fromY, toY)
        val bottom = minOf(fromY, toY)
        for (y in bottom..top) {
            placed += placeIfChanged(level, BlockPos(x, y, z), Blocks.COBBLESTONE)
        }
        return placed
    }

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, block: Block): Int {
        val state = block.defaultBlockState()
        if (level.getBlockState(pos) == state) {
            return 0
        }
        level.setBlockAndUpdate(pos, state)
        return 1
    }
}
