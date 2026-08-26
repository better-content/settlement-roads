package com.bettercontent.settlementroads.debug

import com.bettercontent.settlementroads.planner.model.PathSegment
import com.bettercontent.settlementroads.planner.palette.SurfacePalette
import com.bettercontent.settlementroads.planner.palette.SurfacePaletteSelector
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

object DebugPlanPlacer {
    fun place(level: ServerLevel, rings: List<List<BlockPos>>, segments: List<PathSegment>, isGrassy: Boolean): Int {
        val seed = if (isGrassy) 1L else 2L
        val bridgeSegments = segments.filterIsInstance<PathSegment.Bridge>()
        val bridgeDeckFootprint = buildSet {
            for (segment in bridgeSegments) {
                for ((index, pos) in segment.blocks.withIndex()) {
                    add(pos)
                    for ((offsetX, offsetZ) in lateralOffsets(segment.blocks, index)) {
                        add(pos.offset(offsetX, 0, offsetZ))
                    }
                }
            }
        }
        val groundCenterlines = buildList {
            rings.mapTo(this) { it.dropLast(1) }
            segments.filterIsInstance<PathSegment.Ground>().mapTo(this) { it.blocks }
        }
        var placedBlocks = placeRoad(level, groundCenterlines, seed, isGrassy, bridgeDeckFootprint)

        for (segment in bridgeSegments) {
            placedBlocks += placeBridge(level, segment.blocks)
            placedBlocks += segment.supports.sumOf { support ->
                placeVerticalColumn(
                    level = level,
                    x = support.x,
                    fromY = support.fromY,
                    toY = support.toY,
                    z = support.z,
                    block = Blocks.COBBLESTONE
                )
            }
        }

        return placedBlocks
    }

    private fun placeRoad(
        level: ServerLevel,
        centerlines: List<List<BlockPos>>,
        seed: Long,
        isGrassy: Boolean,
        excludedPositions: Set<BlockPos>
    ): Int {
        var placed = 0
        val roadPositions = buildList {
            for (centerline in centerlines) {
                for ((index, pos) in centerline.withIndex()) {
                    add(pos)
                    for ((offsetX, offsetZ) in lateralOffsets(centerline, index)) {
                        add(pos.offset(offsetX, 0, offsetZ))
                    }
                }
            }
        }
        val centerlinePositions = centerlines.flatten().toSet()
        val pathFootprint = roadPositions.filterTo(linkedSetOf()) { it !in excludedPositions }
        val detailPositions = SurfacePaletteSelector.chooseSparseWeatheringDetail(
            pathFootprint = pathFootprint,
            centerline = centerlinePositions,
            seed = seed,
            detailRate = 0.20
        )
        val mainBlock = when (SurfacePaletteSelector.choose(isGrassy)) {
            SurfacePalette.GRASSY -> Blocks.DIRT_PATH
            SurfacePalette.NON_GRASSY -> Blocks.GRAVEL
        }
        for (pos in pathFootprint) {
            placed += placeIfChanged(level, pos, if (pos in detailPositions) Blocks.COARSE_DIRT else mainBlock)
        }
        return placed
    }

    private fun placeBridge(level: ServerLevel, deck: List<BlockPos>): Int {
        var placed = 0
        for ((index, pos) in deck.withIndex()) {
            val lateralOffsets = lateralOffsets(deck, index)
            placed += placeIfChanged(level, pos, Blocks.STONE_BRICKS)
            for (offset in lateralOffsets) {
                placed += placeIfChanged(level, pos.offset(offset.first, 0, offset.second), Blocks.STONE_BRICKS)
            }

            val wallOffsets = wallOffsets(lateralOffsets)
            for (offset in wallOffsets) {
                placed += placeIfChanged(level, pos.offset(offset.first, 1, offset.second), Blocks.STONE_BRICK_WALL)
            }
        }
        return placed
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

    private fun placeVerticalColumn(
        level: ServerLevel,
        x: Int,
        fromY: Int,
        toY: Int,
        z: Int,
        block: Block
    ): Int {
        var placed = 0
        val top = maxOf(fromY, toY)
        val bottom = minOf(fromY, toY)
        for (y in bottom..top) {
            placed += placeIfChanged(level, BlockPos(x, y, z), block)
        }
        return placed
    }

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, block: Block): Int =
        placeIfChanged(level, pos, block.defaultBlockState())

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, state: BlockState): Int {
        if (level.getBlockState(pos) == state) {
            return 0
        }
        level.setBlockAndUpdate(pos, state)
        return 1
    }
}
