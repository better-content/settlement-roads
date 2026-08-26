package com.bettercontent.settlementroads.planner

data class PlannerConfig(
    val defaultClusterRadius: Int = 134,
    val defaultRingPadding: Int = 3,
    val minClusterSize: Int = 3,
    val scanAllLandStructures: Boolean = true,
    val maxStructuresPerRebuild: Int = 48,
    val minTicksBetweenRebuilds: Long = 1L,
    val maxSegmentsPlacedPerTick: Int = 256,
    val roadWidth: Int = 3,
    val coarseDirtEdgeRate: Double = 0.20,
    val bridgeMaxSpan: Int = 12,
    val bridgeMaxBankGrade: Int = 2,
    val bridgeCostPerBlock: Int = 4,
    val bridgePierSpacing: Int = 5,
    val routeSearchMargin: Int = 12,
    val rerouteStepCost: Int = 10,
    val slopeCostPerBlock: Int = 4,
    val maxSupportProbeDepth: Int = 32,
    val maxLandStructureDepthBelowSurface: Int = 8,
    val maxDryPathVisitedNodes: Int = 4096,
    val maxDryPathDistanceFromLine: Int = 45,
    val allowWaterBridges: Boolean = false
)
