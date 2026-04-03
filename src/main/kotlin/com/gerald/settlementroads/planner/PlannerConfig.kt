package com.gerald.settlementroads.planner

data class PlannerConfig(
    val defaultClusterRadius: Int = 192,
    val defaultRingPadding: Int = 3,
    val minClusterSize: Int = 3,
    val scanAllLandStructures: Boolean = true,
    val maxStructuresPerRebuild: Int = 48,
    val minTicksBetweenRebuilds: Long = 40L,
    val maxSegmentsPlacedPerTick: Int = Int.MAX_VALUE,
    val roadWidth: Int = 3,
    val coarseDirtRate: Double = 0.16,
    val bridgeMaxSpan: Int = 12,
    val bridgeMaxBankGrade: Int = 2,
    val bridgeCostPerBlock: Int = 4,
    val bridgePierSpacing: Int = 5,
    val routeSearchMargin: Int = 24,
    val rerouteStepCost: Int = 10,
    val slopeCostPerBlock: Int = 4,
    val maxSupportProbeDepth: Int = 32,
    val maxLandStructureDepthBelowSurface: Int = 8
)
