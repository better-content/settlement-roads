package com.gerald.fissionreactor.block

import com.gerald.fissionreactor.registry.FissionBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import org.antarcticgardens.cna.content.nuclear.reactor.fuelacceptor.ReactorFuelAcceptorBlock

class FissionFuelAcceptorBlock(properties: BlockBehaviour.Properties) : ReactorFuelAcceptorBlock(properties) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        FissionFuelAcceptorBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? =
        if (type == FissionBlockEntityTypes.FISSION_FUEL_ACCEPTOR.get()) {
            BlockEntityTicker { tickerLevel, tickerPos, tickerState, blockEntity ->
                FissionFuelAcceptorBlockEntity.tickServer(
                    tickerLevel,
                    tickerPos,
                    tickerState,
                    blockEntity as FissionFuelAcceptorBlockEntity
                )
            }
        } else {
            null
        }
}
