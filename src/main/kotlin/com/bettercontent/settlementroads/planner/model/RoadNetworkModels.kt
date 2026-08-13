package com.bettercontent.settlementroads.planner.model

import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox

data class StructureNode(
    val id: String,
    val structureKey: String,
    val center: BlockPos,
    val bounds: BoundingBox,
    val ringPadding: Int,
    val clusterRadius: Int,
    val sourceChunkX: Int? = null,
    val sourceChunkZ: Int? = null
)

data class ClusterPlan(
    val clusterId: Long,
    val structures: List<String>,
    val connections: List<ConnectionPlan>
)

data class RingPath(
    val structureId: String,
    val perimeter: List<BlockPos>
)

data class ConnectionPlan(
    val fromStructureId: String,
    val toStructureId: String,
    val fromRingAnchor: BlockPos,
    val toRingAnchor: BlockPos,
    val segments: List<PathSegment>
) {
    val id: String = listOf(fromStructureId, toStructureId).sorted().joinToString("->")
}

sealed class PathSegment {
    data class Ground(val blocks: List<BlockPos>) : PathSegment()
    data class Bridge(val blocks: List<BlockPos>, val supports: List<SupportColumn>) : PathSegment()
}

data class SupportColumn(
    val x: Int,
    val z: Int,
    val fromY: Int,
    val toY: Int,
    val baseWidth: Int,
    val reachedSolid: Boolean
)

data class ChunkPlacementStamp(
    val chunkX: Int,
    val chunkZ: Int,
    val segmentId: String
)
