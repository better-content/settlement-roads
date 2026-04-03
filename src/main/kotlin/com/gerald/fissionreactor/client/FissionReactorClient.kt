package com.gerald.fissionreactor.client

import com.gerald.fissionreactor.FissionReactorMod
import com.gerald.fissionreactor.client.ponder.FissionPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid = FissionReactorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object FissionReactorClient {
    @JvmStatic
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            PonderIndex.addPlugin(FissionPonderPlugin)
        }
    }
}
