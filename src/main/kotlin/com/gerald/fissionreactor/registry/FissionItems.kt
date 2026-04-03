package com.gerald.fissionreactor.registry

import com.gerald.fissionreactor.FissionReactorMod
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object FissionItems {
    val REGISTER: DeferredRegister<Item> =
        DeferredRegister.create(ForgeRegistries.ITEMS, FissionReactorMod.MOD_ID)

    val FISSION_FUEL_ACCEPTOR: RegistryObject<BlockItem> = REGISTER.register("fission_fuel_acceptor") {
        BlockItem(FissionBlocks.FISSION_FUEL_ACCEPTOR.get(), Item.Properties())
    }
}
