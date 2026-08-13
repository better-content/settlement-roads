package com.bettercontent.settlementroads.command

import com.bettercontent.settlementroads.planner.PlannerConfig
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugScenarioIdTest {
    @Test
    fun parse_handles_known_and_unknown_ids() {
        assertEquals(DebugScenarioId.FLAT_GRASSY_TWINS, DebugScenarioId.parse("flat_grassy_twins"))
        assertNull(DebugScenarioId.parse("nope"))
    }

    @Test
    fun create_structure_nodes_matches_expected_centers_for_river_scenarios() {
        val origin = BlockPos(100, 64, 0)
        val config = PlannerConfig()

        val riverNodes = DebugScenarioId.GRASSY_RIVER_CROSSING.createStructureNodes(origin, config)
        val wideRiverNodes = DebugScenarioId.TOO_WIDE_RIVER.createStructureNodes(origin, config)

        assertEquals("alpha", riverNodes[0].id)
        assertEquals("beta", riverNodes[1].id)
        assertEquals(-16, wideRiverNodes[0].center.x - origin.x)
        assertEquals(16, wideRiverNodes[1].center.x - origin.x)
    }

    @Test
    fun create_definition_marks_non_river_scenario_without_river_x_range() {
        val definition = DebugScenarioId.THREE_STRUCTURE_CLUSTER.createDefinition(BlockPos(0, 64, 0), PlannerConfig())

        assertEquals(DebugScenarioId.THREE_STRUCTURE_CLUSTER, definition.scenarioId)
        assertEquals(null, definition.riverXRange)
        assertEquals(3, definition.structures.size)
    }
}
