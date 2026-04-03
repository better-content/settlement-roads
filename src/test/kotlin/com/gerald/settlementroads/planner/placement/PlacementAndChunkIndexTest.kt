package com.gerald.settlementroads.planner.placement

import com.gerald.settlementroads.planner.model.ConnectionPlan
import com.gerald.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals

class PlacementAndChunkIndexTest {
    private val connection = ConnectionPlan(
        fromStructureId = "alpha",
        toStructureId = "beta",
        fromRingAnchor = BlockPos(14, 64, 0),
        toRingAnchor = BlockPos(18, 64, 0),
        segments = listOf(
            PathSegment.Ground(
                listOf(
                    BlockPos(14, 64, 0),
                    BlockPos(15, 64, 0),
                    BlockPos(16, 64, 0),
                    BlockPos(17, 64, 0),
                    BlockPos(18, 64, 0)
                )
            )
        )
    )

    @Test
    fun placement_idempotent_for_same_plan() {
        val once = SegmentPlacementLedger.apply(FakePlacedWorld(), connection, "minecraft:dirt_path")
        val twice = SegmentPlacementLedger.apply(once, connection, "minecraft:dirt_path")

        assertEquals(once, twice)
    }

    @Test
    fun segment_intersection_per_chunk_stable() {
        val first = SegmentChunkIndexer.index(connection)
        val second = SegmentChunkIndexer.index(connection)

        assertEquals(first, second)
        assertEquals(setOf(0, 1), first.map { it.chunkX }.toSet())
    }
}
