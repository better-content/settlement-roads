package com.gerald.settlementroads.planner.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BridgePlannerTest {
    @Test
    fun bridge_cost_lower_than_detour_when_span_short() {
        val decision = BridgePlanner.evaluateSpan(
            waterSpan = 6,
            rerouteCost = 40,
            maxSpan = 12,
            bankGrade = 1,
            maxBankGrade = 2,
            bridgeCostPerBlock = 4
        )

        assertIs<BridgeDecision.Accepted>(decision)
    }

    @Test
    fun bridge_rejected_when_span_too_wide() {
        val decision = BridgePlanner.evaluateSpan(
            waterSpan = 20,
            rerouteCost = 200,
            maxSpan = 12,
            bankGrade = 0,
            maxBankGrade = 2,
            bridgeCostPerBlock = 4
        )

        assertEquals("span_too_wide", (decision as BridgeDecision.Rejected).reason)
    }

    @Test
    fun bridge_rejected_when_bank_grade_too_steep() {
        val decision = BridgePlanner.evaluateSpan(
            waterSpan = 6,
            rerouteCost = 200,
            maxSpan = 12,
            bankGrade = 4,
            maxBankGrade = 2,
            bridgeCostPerBlock = 4
        )

        assertEquals("bank_grade_too_steep", (decision as BridgeDecision.Rejected).reason)
    }

    @Test
    fun supports_descend_through_water_to_solid() {
        val column = BridgePlanner.descendSupport(
            x = 0,
            z = 0,
            fromY = 70,
            probes = listOf(
                SupportProbe(69, SupportMaterialClass.WATER),
                SupportProbe(68, SupportMaterialClass.AIR),
                SupportProbe(67, SupportMaterialClass.SOLID_SUPPORT)
            )
        )

        assertTrue(column.reachedSolid)
        assertEquals(67, column.toY)
    }

    @Test
    fun supports_ignore_non_solid_terminal_blocks() {
        val column = BridgePlanner.descendSupport(
            x = 0,
            z = 0,
            fromY = 70,
            probes = listOf(
                SupportProbe(69, SupportMaterialClass.LEAVES),
                SupportProbe(68, SupportMaterialClass.LOG),
                SupportProbe(67, SupportMaterialClass.PLANT),
                SupportProbe(66, SupportMaterialClass.SOLID_SUPPORT)
            )
        )

        assertEquals(66, column.toY)
    }

    @Test
    fun soft_ground_widens_foundation() {
        val column = BridgePlanner.descendSupport(
            x = 0,
            z = 0,
            fromY = 70,
            probes = listOf(SupportProbe(66, SupportMaterialClass.SOFT_SUPPORT))
        )

        assertEquals(2, column.baseWidth)
    }

    @Test
    fun mid_piers_inserted_at_spacing_threshold() {
        val piers = BridgePlanner.midPierOffsets(spanLength = 14, pierSpacing = 5)

        assertEquals(listOf(5, 10), piers)
    }
}
