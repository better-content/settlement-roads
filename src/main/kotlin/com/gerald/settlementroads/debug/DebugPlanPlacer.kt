package com.gerald.settlementroads.debug

import com.gerald.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object DebugPlanPlacer {
    fun place(level: ServerLevel, rings: List<List<BlockPos>>, segments: List<PathSegment>, isGrassy: Boolean): Int {
        val mainBlock = if (isGrassy) Blocks.DIRT_PATH else Blocks.GRAVEL
        var placedBlocks = 0

        for (ring in rings) {
            placedBlocks += placeBlocks(level, ring.dropLast(1), mainBlock)
        }

        for (segment in segments) {
            when (segment) {
                is PathSegment.Bridge -> {
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

                is PathSegment.Ground -> {
                    placedBlocks += placeRoad(level, segment.blocks, mainBlock)
                }
            }
        }

        return placedBlocks
    }

    private fun placeRoad(level: ServerLevel, centerline: List<BlockPos>, mainBlock: Block): Int {
        var placed = 0
        for ((index, pos) in centerline.withIndex()) {
            val lateralOffsets = lateralOffsets(centerline, index)
            placed += placeIfChanged(level, pos, mainBlock)
            for (offset in lateralOffsets) {
                placed += placeIfChanged(level, pos.offset(offset.first, 0, offset.second), mainBlock)
            }
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

    private fun placeBlocks(level: ServerLevel, blocks: List<BlockPos>, block: Block): Int =
        blocks.sumOf { placeIfChanged(level, it, block) }

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

    private fun placeIfChanged(level: ServerLevel, pos: BlockPos, block: Block): Int {
        val state = block.defaultBlockState()
        if (level.getBlockState(pos) == state) {
            return 0
        }
        level.setBlockAndUpdate(pos, state)
        return 1
    }
}
