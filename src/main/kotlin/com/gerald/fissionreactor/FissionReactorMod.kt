package com.gerald.fissionreactor

import com.mojang.logging.LogUtils
import com.gerald.fissionreactor.gametest.FissionGameTests
import com.gerald.fissionreactor.registry.FissionBlockEntityTypes
import com.gerald.fissionreactor.registry.FissionBlocks
import com.gerald.fissionreactor.registry.FissionItems
import net.minecraft.world.item.CreativeModeTabs
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.slf4j.Logger

@Mod(FissionReactorMod.MOD_ID)
class FissionReactorMod {
    init {
        val modBus = FMLJavaModLoadingContext.get().modEventBus
        register(modBus)
        FissionGameTests.register()
        LOGGER.info("Loaded mod {}", MOD_ID)
    }

    private fun register(modBus: IEventBus) {
        FissionBlocks.REGISTER.register(modBus)
        FissionItems.REGISTER.register(modBus)
        FissionBlockEntityTypes.REGISTER.register(modBus)
        modBus.addListener(::buildCreativeTabContents)
    }

    private fun buildCreativeTabContents(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.tabKey == CreativeModeTabs.SEARCH) {
            event.accept(FissionItems.FISSION_FUEL_ACCEPTOR)
        }
    }

    companion object {
        const val MOD_ID: String = "fission_reactor"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
