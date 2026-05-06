package com.gerald.settlementroads.data

import com.gerald.settlementroads.command.DebugScenarioId
import com.gerald.settlementroads.command.createDefinition
import com.gerald.settlementroads.planner.ClusterPlanner
import com.gerald.settlementroads.planner.PlannerConfig
import com.gerald.settlementroads.planner.RingPlanner
import com.gerald.settlementroads.planner.RoutePlanner
import com.gerald.settlementroads.planner.model.ChunkPlacementStamp
import com.gerald.settlementroads.planner.model.ClusterPlan
import com.gerald.settlementroads.planner.model.ConnectionPlan
import com.gerald.settlementroads.planner.model.PathSegment
import com.gerald.settlementroads.planner.model.RingPath
import com.gerald.settlementroads.planner.model.StructureNode
import com.gerald.settlementroads.planner.model.SupportColumn
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.levelgen.structure.BoundingBox

data class PlannedRoadNetwork(
    val structures: List<StructureNode> = emptyList(),
    val clusters: List<ClusterPlan> = emptyList(),
    val rings: List<RingPath> = emptyList(),
    val chunkStamps: Set<ChunkPlacementStamp> = emptySet(),
    val appliedSegments: Set<String> = emptySet()
) {
    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.put("structures", ListTag().apply { structures.forEach { add(it.toTag()) } })
        tag.put("clusters", ListTag().apply { clusters.forEach { add(it.toTag()) } })
        tag.put("rings", ListTag().apply { rings.forEach { add(it.toTag()) } })
        tag.put("chunkStamps", ListTag().apply { chunkStamps.forEach { add(it.toTag()) } })
        tag.put("appliedSegments", ListTag().apply { appliedSegments.forEach(::addString) })
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): PlannedRoadNetwork {
            val structures = tag.getList("structures", Tag.TAG_COMPOUND.toInt()).map { structureNodeFromTag(it as CompoundTag) }
            val clusters = tag.getList("clusters", Tag.TAG_COMPOUND.toInt()).map { clusterPlanFromTag(it as CompoundTag) }
            val rings = tag.getList("rings", Tag.TAG_COMPOUND.toInt()).map { ringPathFromTag(it as CompoundTag) }
            val chunkStamps = tag.getList("chunkStamps", Tag.TAG_COMPOUND.toInt()).map { chunkPlacementStampFromTag(it as CompoundTag) }.toSet()

            return PlannedRoadNetwork(
                structures = structures,
                clusters = clusters,
                rings = rings,
                chunkStamps = chunkStamps,
                appliedSegments = tag.getList("appliedSegments", Tag.TAG_STRING.toInt()).map { it.asString }.toSet()
            )
        }

        fun debugScenario(origin: BlockPos, scenarioId: DebugScenarioId, config: PlannerConfig = PlannerConfig()): PlannedRoadNetwork {
            val definition = scenarioId.createDefinition(origin, config)
            val structures = definition.structures
            val rings = structures.map(RingPlanner::plan)
            val ringsByStructureId = rings.associateBy { it.structureId }
            val clusters = ClusterPlanner.plan(structures).map { cluster ->
                val clusterNodes = structures.filter { it.id in cluster.structures }
                cluster.copy(connections = RoutePlanner.planConnections(clusterNodes, ringsByStructureId, definition.terrainProfile, config))
            }
            val chunkStamps = clusters.flatMap(ClusterPlan::connections).flatMapTo(mutableSetOf()) { connection ->
                com.gerald.settlementroads.planner.placement.SegmentChunkIndexer.index(connection)
            }

            return PlannedRoadNetwork(
                structures = structures,
                clusters = clusters,
                rings = rings,
                chunkStamps = chunkStamps
            )
        }
    }
}

data class RoadNetworkState(
    val selectedScenarioId: String? = null,
    val debugOrigin: BlockPos? = null,
    val network: PlannedRoadNetwork = PlannedRoadNetwork(),
    val worldStructures: List<StructureNode> = emptyList(),
    val worldNetwork: PlannedRoadNetwork = PlannedRoadNetwork()
) {
    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        selectedScenarioId?.let { tag.putString("selectedScenarioId", it) }
        debugOrigin?.let { tag.putLong("debugOrigin", it.asLong()) }
        tag.put("network", network.toTag())
        tag.put("worldStructures", ListTag().apply { worldStructures.forEach { add(it.toTag()) } })
        tag.put("worldNetwork", worldNetwork.toTag())
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): RoadNetworkState =
            RoadNetworkState(
                selectedScenarioId = tag.getString("selectedScenarioId").takeIf { it.isNotBlank() },
                debugOrigin = if (tag.contains("debugOrigin")) BlockPos.of(tag.getLong("debugOrigin")) else null,
                network = if (tag.contains("network")) PlannedRoadNetwork.fromTag(tag.getCompound("network")) else PlannedRoadNetwork(),
                worldStructures = tag.getList("worldStructures", Tag.TAG_COMPOUND.toInt()).map { structureNodeFromTag(it as CompoundTag) },
                worldNetwork = if (tag.contains("worldNetwork")) PlannedRoadNetwork.fromTag(tag.getCompound("worldNetwork")) else PlannedRoadNetwork()
            )
    }
}

private fun StructureNode.toTag(): CompoundTag =
    CompoundTag().apply {
        putString("id", this@toTag.id)
        putString("structureKey", structureKey)
        putLong("center", center.asLong())
        putIntArray("bounds", intArrayOf(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()))
        putInt("ringPadding", ringPadding)
        putInt("clusterRadius", clusterRadius)
        sourceChunkX?.let { putInt("sourceChunkX", it) }
        sourceChunkZ?.let { putInt("sourceChunkZ", it) }
    }

private fun ClusterPlan.toTag(): CompoundTag =
    CompoundTag().apply {
        putLong("clusterId", clusterId)
        put("structures", ListTag().apply { structures.forEach(::addString) })
        put("connections", ListTag().apply { connections.forEach { add(it.toTag()) } })
    }

private fun RingPath.toTag(): CompoundTag =
    CompoundTag().apply {
        putString("structureId", structureId)
        putLongArray("perimeter", perimeter.map(BlockPos::asLong))
    }

private fun ConnectionPlan.toTag(): CompoundTag =
    CompoundTag().apply {
        putString("fromStructureId", fromStructureId)
        putString("toStructureId", toStructureId)
        putLong("fromRingAnchor", fromRingAnchor.asLong())
        putLong("toRingAnchor", toRingAnchor.asLong())
        put("segments", ListTag().apply { segments.forEach { add(it.toTag()) } })
    }

private fun PathSegment.toTag(): CompoundTag =
    CompoundTag().apply {
        when (this@toTag) {
            is PathSegment.Bridge -> {
                putString("type", "bridge")
                putLongArray("blocks", blocks.map(BlockPos::asLong))
                put("supports", ListTag().apply { supports.forEach { add(it.toTag()) } })
            }

            is PathSegment.Ground -> {
                putString("type", "ground")
                putLongArray("blocks", blocks.map(BlockPos::asLong))
            }
        }
    }

private fun SupportColumn.toTag(): CompoundTag =
    CompoundTag().apply {
        putInt("x", x)
        putInt("z", z)
        putInt("fromY", fromY)
        putInt("toY", toY)
        putInt("baseWidth", baseWidth)
        putBoolean("reachedSolid", reachedSolid)
    }

private fun ChunkPlacementStamp.toTag(): CompoundTag =
    CompoundTag().apply {
        putInt("chunkX", chunkX)
        putInt("chunkZ", chunkZ)
        putString("segmentId", segmentId)
    }

private fun structureNodeFromTag(tag: CompoundTag): StructureNode =
    StructureNode(
        id = tag.getString("id"),
        structureKey = tag.getString("structureKey"),
        center = BlockPos.of(tag.getLong("center")),
        bounds = tag.getIntArray("bounds").let { BoundingBox(it[0], it[1], it[2], it[3], it[4], it[5]) },
        ringPadding = tag.getInt("ringPadding"),
        clusterRadius = tag.getInt("clusterRadius"),
        sourceChunkX = tag.getIntOrNull("sourceChunkX"),
        sourceChunkZ = tag.getIntOrNull("sourceChunkZ")
    )

private fun clusterPlanFromTag(tag: CompoundTag): ClusterPlan =
    ClusterPlan(
        clusterId = tag.getLong("clusterId"),
        structures = tag.getList("structures", Tag.TAG_STRING.toInt()).map { it.asString },
        connections = tag.getList("connections", Tag.TAG_COMPOUND.toInt()).map { connectionPlanFromTag(it as CompoundTag) }
    )

private fun ringPathFromTag(tag: CompoundTag): RingPath =
    RingPath(
        structureId = tag.getString("structureId"),
        perimeter = tag.getLongArray("perimeter").map(BlockPos::of)
    )

private fun connectionPlanFromTag(tag: CompoundTag): ConnectionPlan =
    ConnectionPlan(
        fromStructureId = tag.getString("fromStructureId"),
        toStructureId = tag.getString("toStructureId"),
        fromRingAnchor = BlockPos.of(tag.getLong("fromRingAnchor")),
        toRingAnchor = BlockPos.of(tag.getLong("toRingAnchor")),
        segments = tag.getList("segments", Tag.TAG_COMPOUND.toInt()).map { pathSegmentFromTag(it as CompoundTag) }
    )

private fun pathSegmentFromTag(tag: CompoundTag): PathSegment {
    val blocks = tag.getLongArray("blocks").map(BlockPos::of)
    return when (tag.getString("type")) {
        "bridge" -> PathSegment.Bridge(
            blocks = blocks,
            supports = tag.getList("supports", Tag.TAG_COMPOUND.toInt()).map { supportColumnFromTag(it as CompoundTag) }
        )

        else -> PathSegment.Ground(blocks)
    }
}

private fun supportColumnFromTag(tag: CompoundTag): SupportColumn =
    SupportColumn(
        x = tag.getInt("x"),
        z = tag.getInt("z"),
        fromY = tag.getInt("fromY"),
        toY = tag.getInt("toY"),
        baseWidth = tag.getInt("baseWidth"),
        reachedSolid = tag.getBoolean("reachedSolid")
    )

private fun chunkPlacementStampFromTag(tag: CompoundTag): ChunkPlacementStamp =
    ChunkPlacementStamp(
        chunkX = tag.getInt("chunkX"),
        chunkZ = tag.getInt("chunkZ"),
        segmentId = tag.getString("segmentId")
    )

private fun ListTag.addString(value: String) {
    add(net.minecraft.nbt.StringTag.valueOf(value))
}

private fun CompoundTag.getIntOrNull(key: String): Int? =
    if (contains(key)) getInt(key) else null
