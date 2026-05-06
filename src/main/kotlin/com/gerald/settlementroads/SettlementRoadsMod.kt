package com.gerald.settlementroads

import com.gerald.settlementroads.command.SettlementRoadsDebugCommands
import com.gerald.settlementroads.config.SettlementRoadsConfig
import com.gerald.settlementroads.runtime.SettlementRoadsRuntime
import com.gerald.settlementroads.worldgen.SettlementRoadsWorldgen
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
        const val MOD_ID: String = "settlementroads"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
