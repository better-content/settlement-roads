package com.gerald.settlementroads.planner

import com.gerald.settlementroads.planner.model.ClusterPlan
import com.gerald.settlementroads.planner.model.StructureNode
import kotlin.math.max

object ClusterPlanner {
    fun plan(structures: List<StructureNode>): List<ClusterPlan> {
        val sorted = structures.sortedBy { it.id }
        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<ClusterPlan>()

        for (node in sorted) {
            if (!visited.add(node.id)) {
                continue
            }

            val component = mutableListOf(node)
            val queue = ArrayDeque<StructureNode>()
            queue.add(node)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (candidate in sorted) {
                    if (candidate.id in visited || !withinClusterRadius(current, candidate)) {
                        continue
                    }

                    visited.add(candidate.id)
                    component.add(candidate)
                    queue.add(candidate)
                }
            }

            val ids = component.map { it.id }.sorted()
            clusters.add(
                ClusterPlan(
                    clusterId = stableClusterId(ids.first()),
                    structures = ids,
                    connections = emptyList()
                )
            )
        }

        return clusters.sortedBy { it.clusterId }
    }

    private fun withinClusterRadius(left: StructureNode, right: StructureNode): Boolean {
        val radius = max(left.clusterRadius, right.clusterRadius)
        val dx = (left.center.x - right.center.x).toLong()
        val dz = (left.center.z - right.center.z).toLong()
        return dx * dx + dz * dz <= radius.toLong() * radius.toLong()
    }

    private fun stableClusterId(seed: String): Long =
        seed.fold(0L) { acc, char -> acc * 31L + char.code.toLong() }.let { if (it < 0L) -it else it }
}
