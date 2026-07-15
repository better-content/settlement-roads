package com.gerald.settlementroads.planner.placement

import com.gerald.settlementroads.planner.model.ConnectionPlan
import com.gerald.settlementroads.planner.model.PathSegment
import com.gerald.settlementroads.planner.model.SupportColumn
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
        val once = SegmentPlacementLedger.apply(FakePlacedWorld(), connection, "minecraft:cobblestone")
        val twice = SegmentPlacementLedger.apply(once, connection, "minecraft:cobblestone")

        assertEquals(once, twice)
    }

    @Test
    fun segment_intersection_per_chunk_stable() {
        val first = SegmentChunkIndexer.index(connection)
        val second = SegmentChunkIndexer.index(connection)

        assertEquals(first, second)
        assertEquals(setOf(0, 1), first.map { it.chunkX }.toSet())
    }

    @Test
    fun segment_ledger_preserves_existing_blocks_across_repeated_positions() {
        val world = FakePlacedWorld()
        val bridgeConnection = connection.copy(
            segments = listOf(
                PathSegment.Bridge(
                    blocks = listOf(BlockPos(14, 64, 0), BlockPos(15, 64, 0)),
                    supports = listOf(SupportColumn(14, 0, 64, 63, 1, reachedSolid = true))
                ),
                PathSegment.Ground(
                    listOf(
                        BlockPos(14, 64, 0),
                        BlockPos(15, 64, 0),
                        BlockPos(16, 64, 0)
                    )
                )
            )
        )
        val overlappingGroundConnection = connection.copy(
            segments = listOf(
                PathSegment.Ground(
                    listOf(
                        BlockPos(14, 64, 0),
                        BlockPos(15, 64, 0),
                        BlockPos(16, 64, 0)
                    )
                )
            )
        )

        val withBridgeThenGround = SegmentPlacementLedger.apply(
            SegmentPlacementLedger.apply(world, bridgeConnection, "minecraft:stone"),
            overlappingGroundConnection,
            "minecraft:cobblestone",
        )
        val withBridgeOnly = SegmentPlacementLedger.apply(world, bridgeConnection, "minecraft:stone")

        assertEquals("minecraft:stone", withBridgeThenGround.blocks[BlockPos(14, 64, 0)])
        assertEquals("minecraft:stone", withBridgeThenGround.blocks[BlockPos(15, 64, 0)])
        assertEquals("minecraft:cobblestone", withBridgeThenGround.blocks[BlockPos(16, 64, 0)])
        assertEquals(withBridgeOnly.appliedSegments.size + 1, withBridgeThenGround.appliedSegments.size)
    }

    @Test
    fun bridge_segment_indexes_chunks_by_each_endpoints() {
        val bridgeConnection = connection.copy(
            segments = listOf(
                PathSegment.Bridge(
                    blocks = listOf(BlockPos(14, 64, 0), BlockPos(15, 64, 16), BlockPos(16, 64, 32)),
                    supports = emptyList()
                )
            )
        )

        val stamps = SegmentChunkIndexer.index(bridgeConnection)

        assertEquals(setOf(0, 1), stamps.map { it.chunkX }.toSet())
        assertEquals(setOf(0, 1, 2), stamps.map { it.chunkZ }.toSet())
    }
}
