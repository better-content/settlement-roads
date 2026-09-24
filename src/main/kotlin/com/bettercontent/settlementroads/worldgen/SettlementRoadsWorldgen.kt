package com.bettercontent.settlementroads.worldgen

import com.bettercontent.settlementroads.SettlementRoadsMod
import com.bettercontent.settlementroads.registry.SettlementRoadsRegistryKeys
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object SettlementRoadsWorldgen {
    val FEATURES: DeferredRegister<Feature<*>> =
        DeferredRegister.create(SettlementRoadsRegistryKeys.FEATURES, SettlementRoadsMod.MOD_ID)
    val PLACEMENT_TYPES: DeferredRegister<StructurePlacementType<*>> =
        DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, SettlementRoadsMod.MOD_ID)

    val TEST_LANDMARK: RegistryObject<Feature<*>> = FEATURES.register("test_landmark", ::TestLandmarkFeature)
    @JvmField
    val CLUSTERED_SPREAD: RegistryObject<StructurePlacementType<*>> = PLACEMENT_TYPES.register("clustered_spread") {
        StructurePlacementType { ClusteredSpreadStructurePlacement.CODEC }
    }

    fun register(modBus: IEventBus) {
        FEATURES.register(modBus)
        PLACEMENT_TYPES.register(modBus)
    }
}
