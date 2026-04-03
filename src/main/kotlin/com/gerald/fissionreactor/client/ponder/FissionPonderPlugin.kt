package com.gerald.fissionreactor.client.ponder

import com.gerald.fissionreactor.FissionReactorMod
import com.gerald.fissionreactor.registry.FissionBlocks
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ItemLike

object FissionPonderPlugin : PonderPlugin {
    private val FISSION_TAG = ResourceLocation(FissionReactorMod.MOD_ID, "fission_reactor")
    private val SHARED_SCHEMATIC = ResourceLocation(FissionReactorMod.MOD_ID, "reactor_core")

    override fun getModId(): String = FissionReactorMod.MOD_ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        val items = helper.withKeyFunction<ItemLike> { BuiltInRegistries.ITEM.getKey(it.asItem()) }

        items.addStoryBoard(
            FissionBlocks.FISSION_FUEL_ACCEPTOR.get(),
            SHARED_SCHEMATIC,
            FissionPonderScenes::fueling,
            FISSION_TAG
        )
        items.addStoryBoard(
            FissionBlocks.FISSION_FUEL_ACCEPTOR.get(),
            SHARED_SCHEMATIC,
            FissionPonderScenes::criticality,
            FISSION_TAG
        )
        items.addStoryBoard(
            FissionBlocks.FISSION_FUEL_ACCEPTOR.get(),
            SHARED_SCHEMATIC,
            FissionPonderScenes::meltdown,
            FISSION_TAG
        )
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag(FISSION_TAG)
            .title("Fission Reactor")
            .description("ChemLib-driven reactor fueling, criticality, and meltdown control.")
            .item(FissionBlocks.FISSION_FUEL_ACCEPTOR.get())
            .addToIndex()
            .register()

        helper.withKeyFunction<ItemLike> { BuiltInRegistries.ITEM.getKey(it.asItem()) }
            .addTagToComponent(FissionBlocks.FISSION_FUEL_ACCEPTOR.get(), FISSION_TAG)
    }
}
