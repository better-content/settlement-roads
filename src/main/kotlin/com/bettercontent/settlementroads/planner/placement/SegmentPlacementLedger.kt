package com.bettercontent.settlementroads.planner.placement

import com.bettercontent.settlementroads.planner.model.ConnectionPlan
import com.bettercontent.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos

data class FakePlacedWorld(
    val blocks: Map<BlockPos, String> = emptyMap(),
    val appliedSegments: Set<String> = emptySet(),
    val protectedBlocks: Set<BlockPos> = emptySet()
)

object SegmentPlacementLedger {
    fun apply(world: FakePlacedWorld, connection: ConnectionPlan, blockId: String): FakePlacedWorld {
        var next = world

        connection.segments.forEachIndexed { index, segment ->
            val stamp = buildStamp(connection.id, index, segment)
            if (stamp in next.appliedSegments) {
                return@forEachIndexed
            }

            val blocks = when (segment) {
                is PathSegment.Bridge -> segment.blocks
                is PathSegment.Ground -> segment.blocks
            }

            val updatedBlocks = next.blocks.toMutableMap()
            val updatedProtectedBlocks = next.protectedBlocks.toMutableSet()
            when (segment) {
                is PathSegment.Bridge -> {
                    for (block in blocks) {
                        updatedBlocks.putIfAbsent(block, blockId)
                        updatedProtectedBlocks += block
                    }
                }
                is PathSegment.Ground -> {
                    for (block in blocks) {
                        if (block !in updatedProtectedBlocks) {
                            updatedBlocks[block] = blockId
                        }
                    }
                }
            }

            next = next.copy(
                blocks = updatedBlocks,
                appliedSegments = next.appliedSegments + stamp,
                protectedBlocks = updatedProtectedBlocks
            )
        }

        return next
    }

    private fun buildStamp(connectionId: String, index: Int, segment: PathSegment): String {
        val shapeKey = when (segment) {
            is PathSegment.Bridge -> buildString {
                append("bridge:")
                append(segment.blocks.joinToString("|") { "${it.x},${it.y},${it.z}" })
                append(":")
                append(
                    segment.supports.joinToString("|") {
                        "${it.x},${it.z},${it.fromY},${it.toY},${it.baseWidth},${it.reachedSolid}"
                    }
                )
            }
            is PathSegment.Ground -> "ground:${segment.blocks.joinToString("|") { "${it.x},${it.y},${it.z}" }}"
        }
        return "$connectionId:$index:$shapeKey"
    }
}
