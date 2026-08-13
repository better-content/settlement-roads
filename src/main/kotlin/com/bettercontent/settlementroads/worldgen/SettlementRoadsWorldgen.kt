package com.bettercontent.settlementroads.worldgen

import com.bettercontent.settlementroads.SettlementRoadsMod
import com.bettercontent.settlementroads.registry.SettlementRoadsRegistryKeys
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object SettlementRoadsWorldgen {
    val FEATURES: DeferredRegister<Feature<*>> =
        DeferredRegister.create(SettlementRoadsRegistryKeys.FEATURES, SettlementRoadsMod.MOD_ID)

    val TEST_LANDMARK: RegistryObject<Feature<*>> = FEATURES.register("test_landmark", ::TestLandmarkFeature)

    fun register(modBus: IEventBus) {
        FEATURES.register(modBus)
    }
}
