package com.gerald.settlementroads.data

import com.gerald.settlementroads.command.DebugScenarioId
import com.gerald.settlementroads.planner.PlannerConfig
import com.gerald.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoadNetworkStateTest {
    @Test
    fun reloading_saved_plan_round_trips() {
        val debugNetwork = PlannedRoadNetwork.debugScenario(BlockPos(0, 64, 0), DebugScenarioId.THREE_STRUCTURE_CLUSTER)
        val worldNetwork = PlannedRoadNetwork.debugScenario(BlockPos(32, 64, 32), DebugScenarioId.GRASSY_RIVER_CROSSING)
            .copy(appliedSegments = setOf("alpha->beta:0"))
        val original = RoadNetworkState(
            selectedScenarioId = DebugScenarioId.THREE_STRUCTURE_CLUSTER.id,
            debugOrigin = BlockPos(0, 64, 0),
            network = debugNetwork,
            worldStructures = worldNetwork.structures,
            worldNetwork = worldNetwork
        )

        val restored = RoadNetworkState.fromTag(original.toTag())

        assertEquals(original, restored)
    }

    @Test
    fun debug_cave_scenario_supports_continue_to_true_solid() {
        val network = PlannedRoadNetwork.debugScenario(
            BlockPos(0, 64, 0),
            DebugScenarioId.CAVE_UNDER_RIVERBED,
            config = PlannerConfig(allowWaterBridges = true)
        )
        val bridge = network.clusters.flatMap { it.connections }.flatMap { it.segments }.first { it is PathSegment.Bridge }

        bridge as PathSegment.Bridge
        assertTrue(bridge.supports.isNotEmpty())
        assertTrue(bridge.supports.all { it.toY == 59 })
    }

    @Test
    fun debug_wide_river_scenario_detours_without_bridge() {
        val network = PlannedRoadNetwork.debugScenario(BlockPos(0, 64, 0), DebugScenarioId.TOO_WIDE_RIVER)
        val segments = network.clusters.flatMap { it.connections }.flatMap { it.segments }

        assertTrue(segments.isNotEmpty())
        assertTrue(segments.none { it is PathSegment.Bridge })
        assertIs<PathSegment.Ground>(segments.single())
    }
}
