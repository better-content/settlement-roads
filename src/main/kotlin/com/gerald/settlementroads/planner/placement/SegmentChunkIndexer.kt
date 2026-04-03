package com.gerald.settlementroads.planner.placement

import com.gerald.settlementroads.planner.model.ChunkPlacementStamp
import com.gerald.settlementroads.planner.model.ConnectionPlan
import com.gerald.settlementroads.planner.model.PathSegment

object SegmentChunkIndexer {
    fun index(connection: ConnectionPlan): Set<ChunkPlacementStamp> =
        connection.segments.flatMapIndexed { segmentIndex, segment ->
            chunkCoordinates(segment).map { (chunkX, chunkZ) ->
                ChunkPlacementStamp(
                    chunkX = chunkX,
                    chunkZ = chunkZ,
                    segmentId = SegmentIdCodec.connection(connection.id, segmentIndex)
                )
            }
        }.toSortedSet(compareBy<ChunkPlacementStamp> { it.chunkX }.thenBy { it.chunkZ }.thenBy { it.segmentId })

    private fun chunkCoordinates(segment: PathSegment): Set<Pair<Int, Int>> {
        val blocks = when (segment) {
            is PathSegment.Bridge -> segment.blocks
            is PathSegment.Ground -> segment.blocks
        }

        return blocks.map { (it.x shr 4) to (it.z shr 4) }.toSet()
    }
}
