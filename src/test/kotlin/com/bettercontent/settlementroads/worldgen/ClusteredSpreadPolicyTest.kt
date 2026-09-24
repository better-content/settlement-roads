package com.bettercontent.settlementroads.worldgen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClusteredSpreadPolicyTest {
    @Test
    fun fixed_seed_site_field_is_stable_and_generation_order_independent() {
        val positions = (-80..80).flatMap { x -> (-80..80).map { z -> x to z } }
        fun selected(order: List<Pair<Int, Int>>) = order.mapNotNull { (x, z) ->
            ClusteredSpreadPolicy.site(0x1234abcdL, x, z, 44712661, 19, 128, 12, 128)?.let { x to z }
        }.toSet()

        val forwards = selected(positions)
        val backwards = selected(positions.reversed())
        assertEquals(forwards, backwards)
        assertTrue(forwards.isNotEmpty())
    }

    @Test
    fun sets_share_centres_but_independent_salts_select_independent_sites() {
        val seed = 812739L
        val first = (-128..127).flatMap { x -> (-128..127).map { z -> x to z } }.mapNotNull { (x, z) ->
            ClusteredSpreadPolicy.site(seed, x, z, 1001, 0, 128, 16, 128)?.let { x to z }
        }.toSet()
        val second = (-128..127).flatMap { x -> (-128..127).map { z -> x to z } }.mapNotNull { (x, z) ->
            ClusteredSpreadPolicy.site(seed, x, z, 2002, 0, 128, 16, 128)?.let { x to z }
        }.toSet()
        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
        assertNotEquals(first, second)
    }

    @Test
    fun radial_falloff_is_zero_outside_radius_and_expected_mass_matches_old_spread() {
        val sum = ClusteredSpreadPolicy.weightSum(centerX = 64, centerZ = 64, cellMinX = 0, cellMinZ = 0, spacing = 128, radius = 12)
        val centerProbability = ClusteredSpreadPolicy.probability(64, 64, 64, 64, 0, 0, 128, 12, 128)
        val outerProbability = ClusteredSpreadPolicy.probability(75, 64, 64, 64, 0, 0, 128, 12, 128)
        assertTrue(sum > 0.0)
        assertEquals(1.0 / sum, centerProbability, 1e-12)
        assertTrue(outerProbability < centerProbability)
        assertEquals(0.0, ClusteredSpreadPolicy.probability(76, 64, 64, 64, 0, 0, 128, 12, 128))
        assertNull(ClusteredSpreadPolicy.site(5L, 76, 64, -1, 0, 128, 12, 128))

        val massWhenActive = (0 until 128).sumOf { x ->
            (0 until 128).sumOf { z ->
                ClusteredSpreadPolicy.probability(x, z, 64, 64, 0, 0, 128, 12, 4096, 2)
            }
        }
        assertEquals(2.0, massWhenActive, 1e-12)
        assertEquals(
            128.0 * 128 / (4096.0 * 4096),
            massWhenActive * ClusteredSpreadPolicy.activationProbability(128, 4096, 2),
            1e-14
        )
    }

    @Test
    fun different_world_seeds_produce_different_cluster_centres_and_validates_bounds() {
        val a = ClusteredSpreadPolicy.center(1L, 30, 30, 0, 128)
        val b = ClusteredSpreadPolicy.center(2L, 30, 30, 0, 128)
        assertNotEquals(a, b)
        assertEquals(
            ClusteredSpreadPolicy.isClusterActive(1L, 20, 20, 0, 128, 4096, 2),
            ClusteredSpreadPolicy.isClusterActive(1L, 100, 100, 0, 128, 4096, 2)
        )
    }

    @Test
    fun rejects_invalid_geometry_and_site_budgets_before_computing_a_field() {
        assertFailsWith<IllegalArgumentException> {
            ClusteredSpreadPolicy.center(1L, 0, 0, 0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ClusteredSpreadPolicy.probability(0, 0, 0, 0, 0, 0, 128, 0, 128)
        }
        assertFailsWith<IllegalArgumentException> {
            ClusteredSpreadPolicy.probability(0, 0, 0, 0, 0, 0, 16, 8, 128)
        }
        assertFailsWith<IllegalArgumentException> {
            ClusteredSpreadPolicy.activationProbability(128, 64, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            ClusteredSpreadPolicy.probability(0, 0, 0, 0, 0, 0, 128, 12, 128, 10_000)
        }
    }
}
