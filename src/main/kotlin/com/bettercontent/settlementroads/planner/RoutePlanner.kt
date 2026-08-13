package com.bettercontent.settlementroads.planner

import com.bettercontent.settlementroads.planner.model.ConnectionPlan
import com.bettercontent.settlementroads.planner.model.PathSegment
import com.bettercontent.settlementroads.planner.model.RingPath
import com.bettercontent.settlementroads.planner.model.StructureNode
import com.bettercontent.settlementroads.planner.terrain.RouteTerrainProfile
import com.bettercontent.settlementroads.planner.terrain.TerrainClass
import com.bettercontent.settlementroads.planner.bridge.BridgeDecision
import com.bettercontent.settlementroads.planner.bridge.BridgePlanner
import net.minecraft.core.BlockPos
import kotlin.math.abs

object RoutePlanner {
    fun planConnections(
        structures: List<StructureNode>,
        rings: Map<String, RingPath>,
        terrainProfile: RouteTerrainProfile? = null,
        config: PlannerConfig = PlannerConfig()
    ): List<ConnectionPlan> {
        if (structures.size < 2) {
            return emptyList()
        }

        val edges = structures
            .flatMapIndexed { index, left ->
                structures.drop(index + 1).map { right ->
                    WeightedEdge(
                        left = left,
                        right = right,
                        weight = distanceSquared(left.center, right.center)
                    )
                }
            }
            .sortedWith(
                compareBy<WeightedEdge> { it.weight }
                    .thenBy { minOf(it.left.id, it.right.id) }
                    .thenBy { maxOf(it.left.id, it.right.id) }
            )

        val parent = structures.associate { it.id to it.id }.toMutableMap()

        fun find(id: String): String {
            var current = id
            while (parent.getValue(current) != current) {
                current = parent.getValue(current)
            }
            return current
        }

        fun union(left: String, right: String) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot
            }
        }

        val chosen = mutableListOf<WeightedEdge>()
        for (edge in edges) {
            if (find(edge.left.id) == find(edge.right.id)) {
                continue
            }

            union(edge.left.id, edge.right.id)
            chosen.add(edge)
        }

        return chosen.map { edge ->
            val fromRing = checkNotNull(rings[edge.left.id]) { "Missing ring for ${edge.left.id}" }
            val toRing = checkNotNull(rings[edge.right.id]) { "Missing ring for ${edge.right.id}" }
            val fromAnchor = RingPlanner.chooseAnchor(fromRing, edge.right.center)
            val toAnchor = RingPlanner.chooseAnchor(toRing, edge.left.center)

            ConnectionPlan(
                fromStructureId = edge.left.id,
                toStructureId = edge.right.id,
                fromRingAnchor = fromAnchor,
                toRingAnchor = toAnchor,
                segments = planSegments(fromAnchor, toAnchor, terrainProfile, config)
            )
        }.filter { it.segments.isNotEmpty() }
    }

    private fun planSegments(
        start: BlockPos,
        end: BlockPos,
        terrainProfile: RouteTerrainProfile?,
        config: PlannerConfig
    ): List<PathSegment> {
        if (terrainProfile == null) {
            return listOf(PathSegment.Ground(manhattanPath(start, end)))
        }

        val directPath = manhattanPath(start, end)
        if (directPath.any { terrainProfile.columnAt(it).terrainClass == TerrainClass.FORBIDDEN }) {
            return emptyList()
        }

        val waterRun = firstWaterRun(directPath, terrainProfile)
            ?: return listOf(PathSegment.Ground(applySurfaceY(directPath, terrainProfile)))

        if (waterRun.first == 0 || waterRun.last == directPath.lastIndex) {
            return emptyList()
        }

        if (!config.allowWaterBridges) {
            val reroutePath = if (terrainProfile.allowDetour) {
                detourPath(directPath[waterRun.first - 1], directPath[waterRun.last + 1], terrainProfile)
            } else {
                emptyList()
            }
            return reroutePath.takeIf { it.isNotEmpty() }?.let {
                listOf(PathSegment.Ground(applySurfaceY(it, terrainProfile)))
            } ?: emptyList()
        }

        val bankBefore = directPath[waterRun.first - 1]
        val bankAfter = directPath[waterRun.last + 1]
        val waterPath = directPath.subList(waterRun.first, waterRun.last + 1)
        val reroutePath = if (terrainProfile.allowDetour) detourPath(bankBefore, bankAfter, terrainProfile) else emptyList()
        val decision = BridgePlanner.evaluateSpan(
            waterSpan = waterPath.size,
            rerouteCost = if (reroutePath.isEmpty()) Int.MAX_VALUE else reroutePath.size,
            maxSpan = config.bridgeMaxSpan,
            bankGrade = abs(terrainProfile.columnAt(bankBefore).surfaceY - terrainProfile.columnAt(bankAfter).surfaceY),
            maxBankGrade = config.bridgeMaxBankGrade,
            bridgeCostPerBlock = config.bridgeCostPerBlock
        )

        val prefix = directPath.subList(0, waterRun.first)
        val suffix = directPath.subList(waterRun.last + 1, directPath.size)

        return when (decision) {
            is BridgeDecision.Accepted -> {
                buildList {
                    if (prefix.isNotEmpty()) {
                        add(PathSegment.Ground(applySurfaceY(prefix, terrainProfile)))
                    }

                    val deck = waterPath.map { pos ->
                        val column = terrainProfile.columnAt(pos)
                        BlockPos(pos.x, column.surfaceY + 1, pos.z)
                    }
                    add(PathSegment.Bridge(deck, bridgeSupports(deck, terrainProfile, config)))

                    if (suffix.isNotEmpty()) {
                        add(PathSegment.Ground(applySurfaceY(suffix, terrainProfile)))
                    }
                }
            }

            is BridgeDecision.Rejected -> {
                if (reroutePath.isEmpty()) {
                    emptyList()
                } else {
                    listOf(PathSegment.Ground(applySurfaceY(reroutePath, terrainProfile)))
                }
            }
        }
    }

    private fun bridgeSupports(
        deck: List<BlockPos>,
        terrainProfile: RouteTerrainProfile,
        config: PlannerConfig
    ) = buildList {
        val indexes = (listOf(0, deck.lastIndex) + BridgePlanner.midPierOffsets(deck.size, config.bridgePierSpacing))
            .distinct()
            .sorted()

        for (index in indexes) {
            val pos = deck[index]
            val column = terrainProfile.columnAt(BlockPos(pos.x, terrainProfile.defaultSurfaceY, pos.z))
            add(BridgePlanner.descendSupport(pos.x, pos.z, pos.y - 1, column.supportProbes))
        }
    }

    private fun firstWaterRun(path: List<BlockPos>, terrainProfile: RouteTerrainProfile): IntRange? {
        var start: Int? = null
        for ((index, pos) in path.withIndex()) {
            val isWater = terrainProfile.columnAt(pos).terrainClass == TerrainClass.SHALLOW_WATER
            if (isWater && start == null) {
                start = index
            }
            if (!isWater && start != null) {
                return start until index
            }
        }
        return start?.let { it..path.lastIndex }
    }

    private fun detourPath(start: BlockPos, end: BlockPos, terrainProfile: RouteTerrainProfile): List<BlockPos> {
        val detourZ = start.z + terrainProfile.detourOffsetZ
        val firstCorner = BlockPos(start.x, start.y, detourZ)
        val secondCorner = BlockPos(end.x, end.y, detourZ)
        return manhattanPath(start, firstCorner) +
            manhattanPath(firstCorner, secondCorner).drop(1) +
            manhattanPath(secondCorner, end).drop(1)
    }

    private fun applySurfaceY(path: List<BlockPos>, terrainProfile: RouteTerrainProfile): List<BlockPos> =
        path.map { pos ->
            val column = terrainProfile.columnAt(pos)
            BlockPos(pos.x, column.surfaceY, pos.z)
        }

    private fun manhattanPath(start: BlockPos, end: BlockPos): List<BlockPos> {
        val blocks = mutableListOf(start)
        var currentX = start.x
        val currentY = start.y
        var currentZ = start.z

        while (currentX != end.x) {
            currentX += if (end.x > currentX) 1 else -1
            blocks.add(BlockPos(currentX, currentY, currentZ))
        }

        while (currentZ != end.z) {
            currentZ += if (end.z > currentZ) 1 else -1
            blocks.add(BlockPos(currentX, currentY, currentZ))
        }

        return blocks
    }

    private fun distanceSquared(left: BlockPos, right: BlockPos): Long {
        val dx = (left.x - right.x).toLong()
        val dz = (left.z - right.z).toLong()
        return dx * dx + dz * dz
    }

    private data class WeightedEdge(
        val left: StructureNode,
        val right: StructureNode,
        val weight: Long
    )
}
