package com.gerald.fissionreactor.registry

import com.gerald.fissionreactor.FissionReactorMod
import com.gerald.fissionreactor.block.FissionFuelAcceptorBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object FissionBlocks {
    val REGISTER: DeferredRegister<net.minecraft.world.level.block.Block> =
        DeferredRegister.create(ForgeRegistries.BLOCKS, FissionReactorMod.MOD_ID)

    val FISSION_FUEL_ACCEPTOR: RegistryObject<FissionFuelAcceptorBlock> = REGISTER.register("fission_fuel_acceptor") {
        FissionFuelAcceptorBlock(
            BlockBehaviour.Properties.of()
                .strength(3.5f)
                .sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops()
        )
    }
}
