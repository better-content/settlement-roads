package com.bettercontent.settlementroads.gametest

import com.bettercontent.settlementroads.SettlementRoadsMod
import com.bettercontent.settlementroads.command.DebugScenarioBuilder
import com.bettercontent.settlementroads.command.DebugScenarioId
import com.bettercontent.settlementroads.command.createDefinition
import com.bettercontent.settlementroads.data.PlannedRoadNetwork
import com.bettercontent.settlementroads.debug.DebugPlanPlacer
import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.event.RegisterGameTestsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(SettlementRoadsMod.MOD_ID)
@Mod.EventBusSubscriber(modid = SettlementRoadsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object SettlementRoadsGameTests {
    private val config = PlannerConfig(allowWaterBridges = true)

    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        event.register(SettlementRoadsGameTests::class.java)
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun grassyRiverCrossingProducesBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.GRASSY_RIVER_CROSSING)

        val bridges = segments.filterIsInstance<PathSegment.Bridge>()
        helper.assertTrue(bridges.isNotEmpty(), "Grassy river crossing should produce at least one bridge segment")
        val bridge = bridges.first()
        helper.assertTrue(bridge.supports.isNotEmpty(), "Bridge should create support columns")
        helper.assertTrue(bridge.supports.all { it.reachedSolid }, "Bridge supports should reach solid terrain")
        helper.assertTrue(
            helper.level.getBlockState(bridge.blocks.first()).`is`(Blocks.STONE_BRICKS),
            "Bridge deck should place stone bricks at ${bridge.blocks.first()}"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun tooWideRiverDetoursWithoutBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.TOO_WIDE_RIVER)
        helper.assertTrue(segments.none { it is PathSegment.Bridge }, "Wide-river scenario should detour instead of bridging")
        helper.assertTrue(
            segments.filterIsInstance<PathSegment.Ground>().flatMap { it.blocks }.any { it.z != helper.absolutePos(BlockPos.ZERO).z },
            "Wide-river scenario should take a detour off the straight center line"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun rockyCrossingUsesWeatheredCobbleAndBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.ROCKY_CROSSING)

        val firstGround = segments.filterIsInstance<PathSegment.Ground>().first()
        helper.assertTrue(
            firstGround.blocks.any { pos -> isWeatheredRoadBlock(helper.level.getBlockState(pos).block) },
            "Rocky crossing should use weathered cobble for ground segments"
        )
        helper.assertTrue(segments.any { it is PathSegment.Bridge }, "Rocky crossing should still use a bridge")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun caveUnderRiverContinuesSupportsToSolid(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.CAVE_UNDER_RIVERBED)
        val bridge = segments.filterIsInstance<PathSegment.Bridge>().first()

        helper.assertTrue(bridge.supports.isNotEmpty(), "Cave scenario bridge should create support columns")
        helper.assertTrue(
            bridge.supports.all { support -> support.reachedSolid && support.toY <= support.fromY - 4 },
            "Cave scenario supports should continue past the hollow riverbed"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun rerunPlacementIsIdempotent(helper: GameTestHelper) {
        val (_, network, _, placedOnce) = spawnAndPlace(helper, DebugScenarioId.FLAT_GRASSY_TWINS)
        val placedTwice = DebugPlanPlacer.place(
            level = helper.level,
            rings = network.rings.map { it.perimeter },
            segments = network.clusters.flatMap { it.connections }.flatMap { it.segments },
            isGrassy = true
        )

        helper.assertTrue(placedOnce > 0, "Initial placement should change the world")
        helper.assertTrue(placedTwice == 0, "Second placement should be idempotent")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun chunkBoundaryScenarioPlacesRoadAcrossBothChunks(helper: GameTestHelper) {
        val (_, network, segments, placed) = spawnAndPlace(helper, DebugScenarioId.CHUNK_BOUNDARY_SPLIT)
        val segmentChunks = segments
            .flatMap {
                when (it) {
                    is PathSegment.Bridge -> it.blocks
                    is PathSegment.Ground -> it.blocks
                }
            }
            .map { it.x shr 4 }
            .toSet()

        helper.assertTrue(placed > 0, "Chunk-boundary scenario should place road blocks")
        helper.assertTrue(network.chunkStamps.map { it.chunkX }.toSet().size >= 2, "Plan should record placement stamps in both chunks")
        helper.assertTrue(segmentChunks.size >= 2, "Road segments should cross the chunk boundary")
        helper.succeed()
    }

    private fun spawnAndPlace(
        helper: GameTestHelper,
        scenarioId: DebugScenarioId
    ): SpawnedScenario {
        val origin = helper.absolutePos(BlockPos(0, 2, 0))
        val definition = scenarioId.createDefinition(origin, config)
        DebugScenarioBuilder.spawn(helper.level, definition)
        val network = PlannedRoadNetwork.debugScenario(origin, scenarioId, config)
        val segments = network.clusters.flatMap { it.connections }.flatMap { it.segments }
        val placed = DebugPlanPlacer.place(
            level = helper.level,
            rings = network.rings.map { it.perimeter },
            segments = segments,
            isGrassy = definition.isGrassy
        )
        return SpawnedScenario(definition, network, segments, placed)
    }

    private fun isWeatheredRoadBlock(block: net.minecraft.world.level.block.Block): Boolean =
        block == Blocks.COBBLESTONE ||
            block == Blocks.MOSSY_COBBLESTONE

    private data class SpawnedScenario(
        val definition: com.bettercontent.settlementroads.command.DebugScenarioDefinition,
        val network: PlannedRoadNetwork,
        val segments: List<PathSegment>,
        val placedBlocks: Int
    )
}
