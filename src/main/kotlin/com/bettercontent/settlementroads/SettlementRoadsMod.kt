package com.bettercontent.settlementroads

import com.bettercontent.settlementroads.command.SettlementRoadsDebugCommands
import com.bettercontent.settlementroads.config.SettlementRoadsConfig
import com.bettercontent.settlementroads.runtime.SettlementRoadsRuntime
import com.bettercontent.settlementroads.worldgen.SettlementRoadsWorldgen
import com.mojang.logging.LogUtils
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.common.Mod
import org.slf4j.Logger

@Mod(SettlementRoadsMod.MOD_ID)
class SettlementRoadsMod {
    init {
        SettlementRoadsConfig.register()
        SettlementRoadsWorldgen.register(FMLJavaModLoadingContext.get().modEventBus)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsDebugCommands::register)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsRuntime::onLevelLoad)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsRuntime::onLevelUnload)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsRuntime::onChunkLoad)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsRuntime::onChunkUnload)
        MinecraftForge.EVENT_BUS.addListener(SettlementRoadsRuntime::onLevelTick)
    }

    companion object {
        const val MOD_ID: String = "settlement_roads"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
