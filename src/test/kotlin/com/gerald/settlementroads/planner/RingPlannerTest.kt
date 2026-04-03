package com.gerald.settlementroads.planner

import com.gerald.settlementroads.planner.model.StructureNode
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingPlannerTest {
    private val structure = StructureNode(
        id = "alpha",
        structureKey = "test/alpha",
        center = BlockPos(0, 64, 0),
        bounds = BoundingBox(-2, 64, -2, 2, 68, 2),
        ringPadding = 3,
        clusterRadius = 32
    )

    @Test
    fun ring_is_closed() {
        val ring = RingPlanner.plan(structure)

        assertTrue(ring.perimeter.isNotEmpty())
        assertEquals(ring.perimeter.first(), ring.perimeter.last())
    }

    @Test
    fun ring_does_not_intersect_bounds() {
        val ring = RingPlanner.plan(structure)

        assertTrue(ring.perimeter.dropLast(1).all { !structure.bounds.isInside(it) })
    }

    @Test
    fun ring_padding_respected() {
        val ring = RingPlanner.plan(structure)

        val minExpectedX = structure.bounds.minX() - structure.ringPadding
        val maxExpectedX = structure.bounds.maxX() + structure.ringPadding
        val minExpectedZ = structure.bounds.minZ() - structure.ringPadding
        val maxExpectedZ = structure.bounds.maxZ() + structure.ringPadding

        assertTrue(ring.perimeter.any { it.x == minExpectedX })
        assertTrue(ring.perimeter.any { it.x == maxExpectedX })
        assertTrue(ring.perimeter.any { it.z == minExpectedZ })
        assertTrue(ring.perimeter.any { it.z == maxExpectedZ })
    }

    @Test
    fun anchor_chooses_nearest_ring_edge_point() {
        val ring = RingPlanner.plan(structure)
        val anchor = RingPlanner.chooseAnchor(ring, BlockPos(20, 64, 0))

        assertTrue(anchor in ring.perimeter)
        assertEquals(structure.bounds.maxX() + structure.ringPadding, anchor.x)
        assertFalse(structure.bounds.isInside(anchor))
    }
}
