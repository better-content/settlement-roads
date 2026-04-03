package com.gerald.settlementroads.planner

import com.gerald.settlementroads.planner.model.StructureNode
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals

class ClusterPlannerTest {
    @Test
    fun clusters_single_structure_isolated() {
        val clusters = ClusterPlanner.plan(listOf(node("alpha", 0, 0)))

        assertEquals(1, clusters.size)
        assertEquals(listOf("alpha"), clusters.single().structures)
    }

    @Test
    fun clusters_two_nearby_structures_together() {
        val clusters = ClusterPlanner.plan(
            listOf(
                node("alpha", 0, 0),
                node("beta", 16, 0)
            )
        )

        assertEquals(1, clusters.size)
        assertEquals(listOf("alpha", "beta"), clusters.single().structures)
    }

    @Test
    fun clusters_chain_connectivity_forms_one_cluster() {
        val clusters = ClusterPlanner.plan(
            listOf(
                node("alpha", 0, 0),
                node("beta", 24, 0),
                node("gamma", 48, 0)
            )
        )

        assertEquals(1, clusters.size)
        assertEquals(listOf("alpha", "beta", "gamma"), clusters.single().structures)
    }

    @Test
    fun clusters_out_of_range_structures_separate() {
        val clusters = ClusterPlanner.plan(
            listOf(
                node("alpha", 0, 0),
                node("beta", 80, 0)
            )
        )

        assertEquals(2, clusters.size)
    }

    private fun node(id: String, x: Int, z: Int): StructureNode =
        StructureNode(
            id = id,
            structureKey = "test/$id",
            center = BlockPos(x, 64, z),
            bounds = BoundingBox(x - 2, 64, z - 2, x + 2, 68, z + 2),
            ringPadding = 3,
            clusterRadius = 32
        )
}
