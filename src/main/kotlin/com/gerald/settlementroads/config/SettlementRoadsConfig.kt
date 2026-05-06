package com.gerald.settlementroads.config

import com.gerald.settlementroads.planner.PlannerConfig
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.config.ModConfig

object SettlementRoadsConfig {
    private val builder = ForgeConfigSpec.Builder()

    private val defaultClusterRadius = builder
        .comment("Cluster radius used to associate structures into settlement groups.")
        .defineInRange("default_cluster_radius", 134, 16, 8192)

    private val defaultRingPadding = builder
        .comment("Padding (in blocks) applied around structures when generating ring anchors.")
        .defineInRange("default_ring_padding", 3, 0, 32)

    private val minClusterSize = builder
        .comment("Minimum number of structures required before considering a cluster.")
        .defineInRange("min_cluster_size", 3, 1, 64)

    private val scanAllLandStructures = builder
        .comment("Whether to include all land-based structures during discovery, or only those with configured depth tags.")
        .define("scan_all_land_structures", true)

    private val maxStructuresPerRebuild = builder
        .comment("Maximum structures considered each rebuild.")
        .defineInRange("max_structures_per_rebuild", 48, 1, 2048)

    private val minTicksBetweenRebuilds = builder
        .comment("Minimum game ticks between world network rebuilds.")
        .defineInRange("min_ticks_between_rebuilds", 40L, 1L, 20_000L)

    private val maxSegmentsPlacedPerTick = builder
        .comment("How many path/bridge segments to apply per tick.")
        .defineInRange("max_segments_placed_per_tick", 64, 1, 10_000)

    private val roadWidth = builder
        .comment("Target road width used by the planner model.")
        .defineInRange("road_width", 3, 1, 16)

    private val bridgeMaxSpan = builder
        .comment("Maximum bridge span allowed before requiring support.")
        .defineInRange("bridge_max_span", 12, 1, 64)

    private val bridgeMaxBankGrade = builder
        .comment("Maximum vertical rise per block step tolerated on bridges.")
        .defineInRange("bridge_max_bank_grade", 2, 0, 32)

    private val bridgeCostPerBlock = builder
        .comment("Additional planning cost for bridge blocks.")
        .defineInRange("bridge_cost_per_block", 4, 1, 1_000)

    private val bridgePierSpacing = builder
        .comment("Bridge pier spacing in blocks.")
        .defineInRange("bridge_pier_spacing", 5, 1, 128)

    private val routeSearchMargin = builder
        .comment("Terrain sample margin around paths used by route planning.")
        .defineInRange("route_search_margin", 12, 1, 256)

    private val rerouteStepCost = builder
        .comment("Cost for each non-direct route step.")
        .defineInRange("reroute_step_cost", 10, 1, 10_000)

    private val slopeCostPerBlock = builder
        .comment("Cost penalty for elevation changes per block.")
        .defineInRange("slope_cost_per_block", 4, 0, 10_000)

    private val maxSupportProbeDepth = builder
        .comment("Maximum depth below terrain top used for bridge support probes.")
        .defineInRange("max_support_probe_depth", 32, 1, 256)

    private val maxLandStructureDepthBelowSurface = builder
        .comment("How deep below surface a structure center can be and still be tracked.")
        .defineInRange("max_land_structure_depth_below_surface", 8, 0, 256)

    private val maxDryPathVisitedNodes = builder
        .comment("A* dry-path search node limit.")
        .defineInRange("max_dry_path_visited_nodes", 4096, 128, 1_000_000)

    private val maxDryPathDistanceFromLine = builder
        .comment("Maximum lateral search distance from the line between endpoints.")
        .defineInRange("max_dry_path_distance_from_line", 45, 1, 2_048)

    private val allowWaterBridges = builder
        .comment("Allow temporary bridge routing over short spans of water.")
        .define("allow_water_bridges", false)

    val printWallInformation = builder
        .comment("Whether wall placement details should be printed. Defaults to false.")
        .define("print_wall_information", false)

    val SPEC: ForgeConfigSpec = builder.build()

    fun register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC)
    }

    fun plannerConfig(): PlannerConfig = PlannerConfig(
        defaultClusterRadius = defaultClusterRadius.get(),
        defaultRingPadding = defaultRingPadding.get(),
        minClusterSize = minClusterSize.get(),
        scanAllLandStructures = scanAllLandStructures.get(),
        maxStructuresPerRebuild = maxStructuresPerRebuild.get(),
        minTicksBetweenRebuilds = minTicksBetweenRebuilds.get(),
        maxSegmentsPlacedPerTick = maxSegmentsPlacedPerTick.get(),
        roadWidth = roadWidth.get(),
        bridgeMaxSpan = bridgeMaxSpan.get(),
        bridgeMaxBankGrade = bridgeMaxBankGrade.get(),
        bridgeCostPerBlock = bridgeCostPerBlock.get(),
        bridgePierSpacing = bridgePierSpacing.get(),
        routeSearchMargin = routeSearchMargin.get(),
        rerouteStepCost = rerouteStepCost.get(),
        slopeCostPerBlock = slopeCostPerBlock.get(),
        maxSupportProbeDepth = maxSupportProbeDepth.get(),
        maxLandStructureDepthBelowSurface = maxLandStructureDepthBelowSurface.get(),
        maxDryPathVisitedNodes = maxDryPathVisitedNodes.get(),
        maxDryPathDistanceFromLine = maxDryPathDistanceFromLine.get(),
        allowWaterBridges = allowWaterBridges.get()
    )
}
