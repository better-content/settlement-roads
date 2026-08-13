package com.bettercontent.settlementroads.runtime

import com.bettercontent.settlementroads.data.PlannedRoadNetwork
import com.bettercontent.settlementroads.planner.ClusterPlanner
import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.RingPlanner
import com.bettercontent.settlementroads.planner.bridge.BridgePlanner
import com.bettercontent.settlementroads.planner.bridge.BridgeDecision
import com.bettercontent.settlementroads.planner.model.ClusterPlan
import com.bettercontent.settlementroads.planner.model.ConnectionPlan
import com.bettercontent.settlementroads.planner.model.PathSegment
import com.bettercontent.settlementroads.planner.model.RingPath
import com.bettercontent.settlementroads.planner.model.StructureNode
import com.bettercontent.settlementroads.planner.terrain.RouteTerrainProfile
import com.bettercontent.settlementroads.planner.terrain.TerrainClass
import com.bettercontent.settlementroads.planner.placement.SegmentChunkIndexer
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import kotlin.math.abs

object WorldNetworkPlanner {
    fun plan(level: ServerLevel, structures: List<StructureNode>, config: PlannerConfig = PlannerConfig()): PlannedRoadNetwork {
        if (structures.isEmpty()) {
            return PlannedRoadNetwork()
        }

        val sortedStructures = prioritizeStructures(level, structures, config).sortedBy { it.id }
        val clustered = ClusterPlanner.plan(sortedStructures)
            .filter { it.structures.size >= config.minClusterSize }
        val plannedStructureIds = clustered.flatMapTo(linkedSetOf()) { it.structures }
        val plannedStructures = sortedStructures.filter { it.id in plannedStructureIds }
        if (plannedStructures.isEmpty()) {
            return PlannedRoadNetwork(structures = emptyList(), clusters = emptyList(), rings = emptyList(), chunkStamps = emptySet())
        }

        val rings = plannedStructures.map(RingPlanner::plan)
        val ringsById = rings.associateBy { it.structureId }
        val clusters = clustered.map { cluster ->
            val clusterNodes = plannedStructures.filter { it.id in cluster.structures }
            cluster.copy(connections = planClusterConnections(level, clusterNodes, ringsById, config))
        }
        val chunkStamps = clusters
            .flatMap(ClusterPlan::connections)
            .flatMapTo(mutableSetOf()) { SegmentChunkIndexer.index(it) }

        return PlannedRoadNetwork(
            structures = plannedStructures,
            clusters = clusters,
            rings = rings,
            chunkStamps = chunkStamps
        )
    }

    private fun prioritizeStructures(level: ServerLevel, structures: List<StructureNode>, config: PlannerConfig): List<StructureNode> {
        if (structures.size <= config.maxStructuresPerRebuild) {
            return structures
        }

        val anchor = level.players().firstOrNull()?.blockPosition() ?: level.sharedSpawnPos
        return structures
            .sortedWith(
                compareBy<StructureNode> { distanceSquared(it.center, anchor) }
                    .thenBy { it.id }
            )
            .take(config.maxStructuresPerRebuild)
    }

    private fun planClusterConnections(
        level: ServerLevel,
        structures: List<StructureNode>,
        ringsById: Map<String, RingPath>,
        config: PlannerConfig
    ): List<ConnectionPlan> {
        if (structures.size < 2) {
            return emptyList()
        }

        val edges = structures
            .flatMapIndexed { index, left ->
                structures.drop(index + 1).map { right ->
                    WeightedEdge(left, right, distanceSquared(left.center, right.center))
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

        return buildList {
            for (edge in edges) {
                if (find(edge.left.id) == find(edge.right.id)) {
                    continue
                }

                union(edge.left.id, edge.right.id)

                val fromRing = checkNotNull(ringsById[edge.left.id]) { "Missing ring for ${edge.left.id}" }
                val toRing = checkNotNull(ringsById[edge.right.id]) { "Missing ring for ${edge.right.id}" }
                val fromAnchor = RingPlanner.chooseAnchor(fromRing, edge.right.center)
                val toAnchor = RingPlanner.chooseAnchor(toRing, edge.left.center)
                val terrain = WorldTerrainSampler.sample(level, fromAnchor, toAnchor, config)
                val candidate = chooseCandidate(fromAnchor, toAnchor, terrain, config)

                if (candidate != null && candidate.segments.isNotEmpty()) {
                    add(
                        ConnectionPlan(
                            fromStructureId = edge.left.id,
                            toStructureId = edge.right.id,
                            fromRingAnchor = fromAnchor,
                            toRingAnchor = toAnchor,
                            segments = candidate.segments
                        )
                    )
                }
            }
        }
    }

    private fun chooseCandidate(
        start: BlockPos,
        end: BlockPos,
        terrain: RouteTerrainProfile,
        config: PlannerConfig
    ): Candidate? {
        val detour = dryPath(start, end, terrain, config)
        val detourCandidate = detour.takeIf { it.isNotEmpty() }?.let {
            Candidate(listOf(PathSegment.Ground(applySurfaceY(it, terrain))), it.size * config.rerouteStepCost)
        }
        val directCandidate = directCandidate(start, end, terrain, detourCandidate?.cost ?: Int.MAX_VALUE, config)

        return listOfNotNull(directCandidate, detourCandidate).minByOrNull { it.cost }
    }

    private fun directCandidate(
        start: BlockPos,
        end: BlockPos,
        terrain: RouteTerrainProfile,
        rerouteCost: Int,
        config: PlannerConfig
    ): Candidate? {
        val path = linePath(start, end)
        if (path.isEmpty()) {
            return null
        }

        if (path.any { terrain.columnAt(it).terrainClass == TerrainClass.FORBIDDEN }) {
            return null
        }

        val waterRuns = contiguousWaterRuns(path, terrain)
        if (waterRuns.isEmpty()) {
            return Candidate(
                segments = listOf(PathSegment.Ground(applySurfaceY(path, terrain))),
                cost = path.size * config.rerouteStepCost
            )
        }

        if (!config.allowWaterBridges) {
            return null
        }

        if (waterRuns.any { it.first == 0 || it.last == path.lastIndex }) {
            return null
        }

        val segments = mutableListOf<PathSegment>()
        var directCost = 0
        var cursor = 0

        for (run in waterRuns) {
            val groundPrefix = path.subList(cursor, run.first)
            if (groundPrefix.isNotEmpty()) {
                segments += PathSegment.Ground(applySurfaceY(groundPrefix, terrain))
                directCost += groundPrefix.size * config.rerouteStepCost
            }

            val bankBefore = path[run.first - 1]
            val bankAfter = path[run.last + 1]
            val waterPath = path.subList(run.first, run.last + 1)
            val bankGrade = abs(terrain.columnAt(bankBefore).surfaceY - terrain.columnAt(bankAfter).surfaceY)
            when (val decision = BridgePlanner.evaluateSpan(
                waterSpan = waterPath.size,
                rerouteCost = rerouteCost,
                maxSpan = config.bridgeMaxSpan,
                bankGrade = bankGrade,
                maxBankGrade = config.bridgeMaxBankGrade,
                bridgeCostPerBlock = config.bridgeCostPerBlock
            )) {
                is BridgeDecision.Accepted -> {
                    val deck = waterPath.map { pos ->
                        val column = terrain.columnAt(pos)
                        BlockPos(pos.x, column.surfaceY + 1, pos.z)
                    }
                    segments += PathSegment.Bridge(deck, bridgeSupports(deck, terrain, config))
                    directCost += decision.bridgeCost
                }

                is BridgeDecision.Rejected -> return null
            }

            cursor = run.last + 1
        }

        val suffix = path.subList(cursor, path.size)
        if (suffix.isNotEmpty()) {
            segments += PathSegment.Ground(applySurfaceY(suffix, terrain))
            directCost += suffix.size * config.rerouteStepCost
        }

        return Candidate(segments, directCost)
    }

    private fun bridgeSupports(deck: List<BlockPos>, terrain: RouteTerrainProfile, config: PlannerConfig) =
        buildList {
            val indexes = (listOf(0, deck.lastIndex) + BridgePlanner.midPierOffsets(deck.size, config.bridgePierSpacing))
                .distinct()
                .sorted()

            for (index in indexes) {
                val pos = deck[index]
                val column = terrain.columnAt(pos)
                add(BridgePlanner.descendSupport(pos.x, pos.z, pos.y - 1, column.supportProbes))
            }
        }

    private fun dryPath(start: BlockPos, end: BlockPos, terrain: RouteTerrainProfile, config: PlannerConfig): List<BlockPos> {
        val startNode = Node(start.x, start.z)
        val endNode = Node(end.x, end.z)
        val open = mutableSetOf(startNode)
        val cameFrom = mutableMapOf<Node, Node>()
        val gScore = mutableMapOf(startNode to 0)
        val fScore = mutableMapOf(startNode to heuristic(startNode, endNode, config))
        val visited = mutableSetOf<Node>()
        val minX = minOf(start.x, end.x) - config.maxDryPathDistanceFromLine
        val maxX = maxOf(start.x, end.x) + config.maxDryPathDistanceFromLine
        val minZ = minOf(start.z, end.z) - config.maxDryPathDistanceFromLine
        val maxZ = maxOf(start.z, end.z) + config.maxDryPathDistanceFromLine

        while (open.isNotEmpty()) {
            val current = open.minByOrNull { fScore[it] ?: Int.MAX_VALUE } ?: break
            if (current == endNode) {
                return reconstructPath(current, cameFrom, terrain)
            }

            open.remove(current)
            if (!visited.add(current)) {
                continue
            }
            if (visited.size > config.maxDryPathVisitedNodes) {
                return emptyList()
            }

            for ((nextX, nextZ) in neighbors(current.x, current.z)) {
                if (nextX < minX || nextX > maxX || nextZ < minZ || nextZ > maxZ) {
                    continue
                }
                val next = Node(nextX, nextZ)
                if (visited.contains(next)) {
                    continue
                }

                val nextColumn = terrain.columnAt(BlockPos(nextX, terrain.defaultSurfaceY, nextZ))
                if (nextColumn.terrainClass != TerrainClass.WALKABLE) {
                    continue
                }

                val currentColumn = terrain.columnAt(BlockPos(current.x, terrain.defaultSurfaceY, current.z))
                val tentative = (gScore[current] ?: Int.MAX_VALUE) +
                    config.rerouteStepCost +
                    abs(nextColumn.surfaceY - currentColumn.surfaceY) * config.slopeCostPerBlock

                if (tentative >= (gScore[next] ?: Int.MAX_VALUE)) {
                    continue
                }

                cameFrom[next] = current
                gScore[next] = tentative
                fScore[next] = tentative + heuristic(next, endNode, config)
                open += next
            }
        }

        return emptyList()
    }

    private fun reconstructPath(end: Node, cameFrom: Map<Node, Node>, terrain: RouteTerrainProfile): List<BlockPos> {
        val path = mutableListOf(end)
        var current = end
        while (true) {
            current = cameFrom[current] ?: break
            path += current
        }

        return path
            .asReversed()
            .map { node ->
                val column = terrain.columnAt(BlockPos(node.x, terrain.defaultSurfaceY, node.z))
                BlockPos(node.x, column.surfaceY, node.z)
            }
    }

    private fun contiguousWaterRuns(path: List<BlockPos>, terrain: RouteTerrainProfile): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start: Int? = null
        for ((index, pos) in path.withIndex()) {
            val isWater = terrain.columnAt(pos).terrainClass == TerrainClass.SHALLOW_WATER
            if (isWater && start == null) {
                start = index
            }
            if (!isWater && start != null) {
                runs += start until index
                start = null
            }
        }
        if (start != null) {
            runs += start..path.lastIndex
        }
        return runs
    }

    private fun applySurfaceY(path: List<BlockPos>, terrain: RouteTerrainProfile): List<BlockPos> =
        path.map { pos ->
            val column = terrain.columnAt(pos)
            BlockPos(pos.x, column.surfaceY, pos.z)
        }

    private fun linePath(start: BlockPos, end: BlockPos): List<BlockPos> {
        var x0 = start.x
        var z0 = start.z
        val x1 = end.x
        val z1 = end.z
        val dx = abs(x1 - x0)
        val dz = abs(z1 - z0)
        val sx = if (x0 < x1) 1 else -1
        val sz = if (z0 < z1) 1 else -1
        var error = dx - dz
        val result = mutableListOf<BlockPos>()

        while (true) {
            result += BlockPos(x0, start.y, z0)
            if (x0 == x1 && z0 == z1) {
                break
            }

            val error2 = error * 2
            if (error2 > -dz) {
                error -= dz
                x0 += sx
            }
            if (error2 < dx) {
                error += dx
                z0 += sz
            }
        }

        return result
    }

    private fun neighbors(x: Int, z: Int): List<Pair<Int, Int>> =
        listOf(
            (x + 1) to z,
            (x - 1) to z,
            x to (z + 1),
            x to (z - 1)
        )

    private fun heuristic(node: Node, end: Node, config: PlannerConfig): Int =
        (abs(node.x - end.x) + abs(node.z - end.z)) * config.rerouteStepCost

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

    private data class Candidate(
        val segments: List<PathSegment>,
        val cost: Int
    )

    private data class Node(
        val x: Int,
        val z: Int
    )
}
