package com.bettercontent.settlementroads.config

import com.bettercontent.settlementroads.planner.PlannerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SettlementRoadsConfigTest {
    @Test
    fun planner_defaults_match_expected_defaults() {
        val plannerConfig = PlannerConfig()

        assertEquals(134, plannerConfig.defaultClusterRadius)
        assertEquals(3, plannerConfig.defaultRingPadding)
        assertEquals(3, plannerConfig.minClusterSize)
        assertEquals(true, plannerConfig.scanAllLandStructures)
        assertEquals(48, plannerConfig.maxStructuresPerRebuild)
        assertEquals(1L, plannerConfig.minTicksBetweenRebuilds)
        assertEquals(256, plannerConfig.maxSegmentsPlacedPerTick)
        assertEquals(3, plannerConfig.roadWidth)
        assertEquals(12, plannerConfig.bridgeMaxSpan)
        assertEquals(2, plannerConfig.bridgeMaxBankGrade)
        assertEquals(4, plannerConfig.bridgeCostPerBlock)
        assertEquals(5, plannerConfig.bridgePierSpacing)
        assertEquals(12, plannerConfig.routeSearchMargin)
        assertEquals(10, plannerConfig.rerouteStepCost)
        assertEquals(4, plannerConfig.slopeCostPerBlock)
        assertEquals(32, plannerConfig.maxSupportProbeDepth)
        assertEquals(8, plannerConfig.maxLandStructureDepthBelowSurface)
        assertEquals(4096, plannerConfig.maxDryPathVisitedNodes)
        assertEquals(45, plannerConfig.maxDryPathDistanceFromLine)
        assertEquals(false, plannerConfig.allowWaterBridges)
    }

    @Test
    fun planner_config_copy_can_adjust_values_for_pathing_tuning() {
        val tuned = PlannerConfig(roadWidth = 5, maxSegmentsPlacedPerTick = 12)

        assertNotEquals(tuned.roadWidth, PlannerConfig().roadWidth)
        assertNotEquals(tuned.maxSegmentsPlacedPerTick, PlannerConfig().maxSegmentsPlacedPerTick)
    }
}
