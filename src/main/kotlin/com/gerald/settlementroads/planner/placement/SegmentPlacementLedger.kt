package com.gerald.settlementroads.planner.placement

import com.gerald.settlementroads.planner.model.ConnectionPlan
import com.gerald.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos

data class FakePlacedWorld(
    val blocks: Map<BlockPos, String> = emptyMap(),
    val appliedSegments: Set<String> = emptySet()
)

object SegmentPlacementLedger {
    fun apply(world: FakePlacedWorld, connection: ConnectionPlan, blockId: String): FakePlacedWorld {
        var next = world

        connection.segments.forEachIndexed { index, segment ->
            val stamp = "${connection.id}:$index"
            if (stamp in next.appliedSegments) {
                return@forEachIndexed
            }

            val blocks = when (segment) {
                is PathSegment.Bridge -> segment.blocks
                is PathSegment.Ground -> segment.blocks
            }

            val updatedBlocks = next.blocks.toMutableMap()
            for (block in blocks) {
                updatedBlocks.putIfAbsent(block, blockId)
            }

            next = next.copy(
                blocks = updatedBlocks,
                appliedSegments = next.appliedSegments + stamp
            )
        }

        return next
    }
}
