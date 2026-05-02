package com.gerald.settlementroads.debug

import com.gerald.settlementroads.planner.model.PathSegment
import com.gerald.settlementroads.planner.placement.WeatheredRoadPalette
import com.gerald.settlementroads.planner.placement.WeatheredRoadPiece
import com.gerald.settlementroads.planner.placement.WeatheredWallPiece
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

object DebugPlanPlacer {
    fun place(level: ServerLevel, rings: List<List<BlockPos>>, segments: List<PathSegment>, isGrassy: Boolean): Int {
        val seed = if (isGrassy) 1L else 2L
        var placedBlocks = 0

        for (ring in rings) {
            placedBlocks += placeRoad(level, ring.dropLast(1), "debug-ring".hashCode().toLong() + seed)
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
                    placedBlocks += placeRoad(level, segment.blocks, "debug-ground".hashCode().toLong() + seed)
                }
            }
        }

        return placedBlocks
    }

    private fun placeRoad(level: ServerLevel, centerline: List<BlockPos>, seed: Long): Int {
        var placed = 0
        for ((index, pos) in centerline.withIndex()) {
            val lateralOffsets = lateralOffsets(centerline, index)
            placed += placeRoadPiece(level, pos, seed, index, centerline = true)
            for (offset in lateralOffsets) {
                placed += placeRoadPiece(level, pos.offset(offset.first, 0, offset.second), seed, index, centerline = false)
            }
            for (offset in wallOffsets(lateralOffsets)) {
                placed += placeWallPiece(level, pos.offset(offset.first, 1, offset.second), seed, index)
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

    private fun placeRoadPiece(
        level: ServerLevel,
        pos: BlockPos,
        seed: Long,
        index: Int,
        centerline: Boolean
    ): Int {
        val piece = WeatheredRoadPalette.choose(pos, seed, index, centerline) ?: return 0
        return placeIfChanged(level, pos, roadState(piece))
    }

    private fun roadState(piece: WeatheredRoadPiece): BlockState =
        when (piece) {
            WeatheredRoadPiece.COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState()
            WeatheredRoadPiece.MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState()
        }

    private fun placeWallPiece(level: ServerLevel, pos: BlockPos, seed: Long, index: Int): Int {
        if (!level.getBlockState(pos).isAir) {
            return 0
        }

        val piece = WeatheredRoadPalette.chooseWall(pos, seed, index) ?: return 0
        return placeIfChanged(level, pos, wallState(piece))
    }

    private fun wallState(piece: WeatheredWallPiece): BlockState =
        when (piece) {
            WeatheredWallPiece.COBBLESTONE_WALL -> Blocks.COBBLESTONE_WALL.defaultBlockState()
            WeatheredWallPiece.MOSSY_COBBLESTONE_WALL -> Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
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
