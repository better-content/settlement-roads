package com.gerald.settlementroads.planner.placement

import net.minecraft.core.BlockPos

enum class WeatheredRoadPiece {
    COBBLESTONE,
    MOSSY_COBBLESTONE
}

enum class WeatheredWallPiece {
    COBBLESTONE_WALL,
    MOSSY_COBBLESTONE_WALL
}

object WeatheredRoadPalette {
    private const val GUIDE_INTERVAL = 18
    private const val GUIDE_LENGTH = 1
    private const val WALL_INTERVAL = 26

    fun choose(pos: BlockPos, seed: Long, index: Int, centerline: Boolean): WeatheredRoadPiece? {
        if (!inGuideMark(seed, index, centerline)) {
            return null
        }

        val roll = roll(pos, seed)
        if (!centerline && roll < 65) {
            return null
        }

        return when {
            roll < 32 -> WeatheredRoadPiece.MOSSY_COBBLESTONE
            else -> WeatheredRoadPiece.COBBLESTONE
        }
    }

    fun chooseWall(pos: BlockPos, seed: Long, index: Int): WeatheredWallPiece? {
        val phase = Math.floorMod(seed xor 0x5f3759dfL, WALL_INTERVAL.toLong()).toInt()
        if (Math.floorMod(index + phase, WALL_INTERVAL) != 0) {
            return null
        }

        return if (roll(pos, seed xor 0x632be59bd9b4e019L) < 35) {
            WeatheredWallPiece.MOSSY_COBBLESTONE_WALL
        } else {
            WeatheredWallPiece.COBBLESTONE_WALL
        }
    }

    private fun inGuideMark(seed: Long, index: Int, centerline: Boolean): Boolean {
        val phase = Math.floorMod(seed, GUIDE_INTERVAL.toLong()).toInt()
        val offset = Math.floorMod(index + phase, GUIDE_INTERVAL)
        val length = if (centerline) GUIDE_LENGTH else 1
        return offset < length
    }

    private fun roll(pos: BlockPos, seed: Long): Int {
        var value = seed
        value = value xor (pos.x.toLong() * 341873128712L)
        value = value xor (pos.y.toLong() * 132897987541L)
        value = value xor (pos.z.toLong() * 42317861L)
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        return Math.floorMod(value, 100L).toInt()
    }
}
