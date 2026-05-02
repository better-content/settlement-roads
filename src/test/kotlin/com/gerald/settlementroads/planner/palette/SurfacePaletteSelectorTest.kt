package com.gerald.settlementroads.planner.palette

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfacePaletteSelectorTest {
    @Test
    fun grassy_surface_chooses_cobblestone() {
        assertEquals("minecraft:cobblestone", SurfacePaletteSelector.choose(true).mainBlockId)
    }

    @Test
    fun non_grassy_surface_chooses_cobblestone() {
        assertEquals("minecraft:cobblestone", SurfacePaletteSelector.choose(false).mainBlockId)
    }

    @Test
    fun weathering_noise_is_sparse() {
        val footprint = buildSet {
            for (z in 0..15) {
                add(BlockPos(0, 64, z))
                add(BlockPos(1, 64, z))
                add(BlockPos(2, 64, z))
            }
        }
        val centerline = (0..15).map { BlockPos(1, 64, it) }.toSet()

        val detail = SurfacePaletteSelector.chooseSparseWeatheringDetail(
            pathFootprint = footprint,
            centerline = centerline,
            seed = 42L,
            detailRate = 0.2
        )

        assertTrue(detail.none { it in centerline })
        assertTrue(detail.size < footprint.size / 4)
    }
}
