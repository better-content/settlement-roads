package com.gerald.settlementroads.planner.placement

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatheredRoadPaletteTest {
    @Test
    fun weathered_palette_is_deterministic() {
        val pos = BlockPos(12, 64, -7)
        val first = WeatheredRoadPalette.choose(pos, seed = 42L, index = 2, centerline = true)
        val second = WeatheredRoadPalette.choose(pos, seed = 42L, index = 2, centerline = true)

        assertEquals(first, second)
    }

    @Test
    fun weathered_palette_mixes_full_cobble_blocks_and_skips_some_edges() {
        val pieces = buildList {
            for (x in 0..63) {
                for (z in -1..1) {
                    add(WeatheredRoadPalette.choose(BlockPos(x, 64, z), seed = 99L, index = x, centerline = z == 0))
                }
            }
        }

        assertTrue(null in pieces)
        assertTrue(WeatheredRoadPiece.COBBLESTONE in pieces)
        assertTrue(WeatheredRoadPiece.MOSSY_COBBLESTONE in pieces)
    }

    @Test
    fun weathered_palette_leaves_long_breaks_between_guide_marks() {
        val centerline = (0..63).map { index ->
            WeatheredRoadPalette.choose(BlockPos(index, 64, 0), seed = 99L, index = index, centerline = true)
        }
        val longestBreak = centerline
            .joinToString("") { if (it == null) "." else "#" }
            .split("#")
            .maxOf(String::length)

        assertTrue(longestBreak >= 8)
        assertTrue(centerline.count { it != null } < centerline.size / 3)
    }

    @Test
    fun weathered_wall_palette_is_sparse_and_uses_cobble_walls() {
        val walls = (0..127).map { index ->
            WeatheredRoadPalette.chooseWall(BlockPos(index, 65, 2), seed = 99L, index = index)
        }

        assertTrue(null in walls)
        assertTrue(WeatheredWallPiece.COBBLESTONE_WALL in walls)
        assertTrue(WeatheredWallPiece.MOSSY_COBBLESTONE_WALL in walls)
        assertTrue(walls.count { it != null } < walls.size / 10)
    }
}
