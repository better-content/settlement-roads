package com.gerald.settlementroads.command

import com.gerald.settlementroads.config.SettlementRoadsConfig
import com.gerald.settlementroads.data.PlannedRoadNetwork
import com.gerald.settlementroads.data.SettlementRoadsSavedData
import com.gerald.settlementroads.debug.DebugPlanPlacer
import com.gerald.settlementroads.planner.PlannerConfig
import com.gerald.settlementroads.runtime.SettlementRoadsRuntime
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraftforge.event.RegisterCommandsEvent

object SettlementRoadsDebugCommands {
    private val plannerConfig: PlannerConfig
        get() = SettlementRoadsConfig.plannerConfig()

    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("settlementroads")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("debug")
                        .then(
                            Commands.literal("spawn_scenario")
                                .then(
                                    Commands.argument("id", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(DebugScenarioId.entries.map { it.id }, builder)
                                        }
                                        .executes(::spawnScenario)
                                )
                        )
                        .then(Commands.literal("plan_here").executes(::planHere))
                        .then(Commands.literal("place_here").executes(::placeHere))
                        .then(Commands.literal("clear_here").executes(::clearHere))
                )
                .then(
                    Commands.literal("world")
                        .then(Commands.literal("status").executes(::worldStatus))
                        .then(Commands.literal("scan_loaded").executes(::worldScanLoaded))
                        .then(Commands.literal("rebuild").executes(::worldScanLoaded))
                        .then(Commands.literal("place_loaded").executes(::worldPlaceLoaded))
                        .then(Commands.literal("clear").executes(::worldClear))
                )
        )
    }

    private fun spawnScenario(context: CommandContext<CommandSourceStack>): Int {
        val scenario = DebugScenarioId.parse(StringArgumentType.getString(context, "id"))
            ?: throw IllegalArgumentException("Unknown scenario")
        val source = context.source
        val origin = source.playerOrException.blockPosition()
        val definition = scenario.createDefinition(origin, plannerConfig)

        DebugScenarioBuilder.spawn(source.level, definition)

        SettlementRoadsSavedData.get(source.level).update {
            it.copy(
                selectedScenarioId = scenario.id,
                debugOrigin = origin,
                network = PlannedRoadNetwork()
            )
        }
        return 1
    }

    private fun planHere(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val savedData = SettlementRoadsSavedData.get(source.level)
        val state = savedData.state
        val scenario = state.selectedScenarioId?.let(DebugScenarioId::parse)
            ?: DebugScenarioId.FLAT_GRASSY_TWINS
        val origin = state.debugOrigin ?: source.playerOrException.blockPosition()
        val definition = scenario.createDefinition(origin, plannerConfig)
        DebugScenarioBuilder.spawn(source.level, definition)
        val network = PlannedRoadNetwork.debugScenario(origin, scenario, plannerConfig)

        savedData.update {
            it.copy(
                selectedScenarioId = scenario.id,
                debugOrigin = origin,
                network = network
            )
        }
        return 1
    }

    private fun placeHere(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val state = SettlementRoadsSavedData.get(source.level).state
        val scenario = state.selectedScenarioId?.let(DebugScenarioId::parse) ?: DebugScenarioId.FLAT_GRASSY_TWINS
        val level = source.level

        DebugPlanPlacer.place(
            level = level,
            rings = state.network.rings.map { it.perimeter },
            segments = state.network.clusters.flatMap { it.connections }.flatMap { it.segments },
            isGrassy = scenario.isGrassy
        )

        return 1
    }

    private fun clearHere(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val state = SettlementRoadsSavedData.get(source.level).state
        val scenario = state.selectedScenarioId?.let(DebugScenarioId::parse)
        val origin = state.debugOrigin
        if (scenario != null && origin != null) {
            DebugScenarioBuilder.clear(source.level, scenario.createDefinition(origin, plannerConfig))
        }
        SettlementRoadsSavedData.get(source.level).update {
            it.copy(
                selectedScenarioId = null,
                debugOrigin = null,
                network = PlannedRoadNetwork()
            )
        }
        return 1
    }

    private fun worldStatus(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        SettlementRoadsRuntime.status(source.level)
        return 1
    }

    private fun worldScanLoaded(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        SettlementRoadsRuntime.rebuildFromLoadedChunks(source.level)
        return 1
    }

    private fun worldPlaceLoaded(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        SettlementRoadsRuntime.placeAvailable(source.level)
        return 1
    }

    private fun worldClear(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        SettlementRoadsRuntime.clear(source.level)
        return 1
    }
}
