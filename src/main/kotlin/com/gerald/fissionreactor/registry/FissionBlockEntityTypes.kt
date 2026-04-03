package com.gerald.fissionreactor.registry

import com.gerald.fissionreactor.FissionReactorMod
import com.gerald.fissionreactor.block.FissionFuelAcceptorBlockEntity
import com.mojang.datafixers.types.Type
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object FissionBlockEntityTypes {
    val REGISTER: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FissionReactorMod.MOD_ID)

    val FISSION_FUEL_ACCEPTOR: RegistryObject<BlockEntityType<FissionFuelAcceptorBlockEntity>> =
        REGISTER.register("fission_fuel_acceptor") {
            BlockEntityType.Builder.of(
                BlockEntitySupplier(::FissionFuelAcceptorBlockEntity),
                FissionBlocks.FISSION_FUEL_ACCEPTOR.get()
            ).build(null as Type<*>?)
        }
}
