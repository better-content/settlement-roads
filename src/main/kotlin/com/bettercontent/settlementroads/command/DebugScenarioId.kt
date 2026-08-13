package com.bettercontent.settlementroads.command

import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.model.StructureNode
import net.minecraft.core.BlockPos

enum class DebugScenarioId(
    val id: String,
    val isGrassy: Boolean
) {
    FLAT_GRASSY_TWINS("flat_grassy_twins", true),
    GRASSY_RIVER_CROSSING("grassy_river_crossing", true),
    ROCKY_CROSSING("rocky_crossing", false),
    CAVE_UNDER_RIVERBED("cave_under_riverbed", true),
    TOO_WIDE_RIVER("too_wide_river", true),
    THREE_STRUCTURE_CLUSTER("three_structure_cluster", true),
    RERUN_STABILITY("rerun_stability", true),
    CHUNK_BOUNDARY_SPLIT("chunk_boundary_split", true);

    fun createStructureNodes(origin: BlockPos, config: PlannerConfig): List<StructureNode> {
        return createDefinition(origin, config).structures
    }

    companion object {
        fun parse(raw: String): DebugScenarioId? =
            entries.firstOrNull { it.id == raw }
    }
}
