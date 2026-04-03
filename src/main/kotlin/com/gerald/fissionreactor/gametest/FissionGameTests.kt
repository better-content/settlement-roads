package com.gerald.fissionreactor.gametest

import com.gerald.fissionreactor.FissionReactorMod
import com.gerald.fissionreactor.block.FissionFuelAcceptorBlockEntity
import com.gerald.fissionreactor.registry.FissionBlocks
import com.smashingmods.chemlib.registry.ItemRegistry
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import org.antarcticgardens.cna.CNABlocks
import org.antarcticgardens.cna.content.nuclear.reactor.rod.ReactorRodBlockEntity

@GameTestHolder(FissionReactorMod.MOD_ID)
@PrefixGameTestTemplate(false)
object FissionGameTests {
    fun register() {
        // Forge scans @GameTestHolder classes automatically once the namespace is enabled.
    }

    @JvmStatic
    @GameTest(
        templateNamespace = FissionReactorMod.MOD_ID,
        template = "uranium_smoke",
        batch = "fission_reactor",
        timeoutTicks = 120
    )
    fun uraniumFuelFeedsRod(helper: GameTestHelper) {
        val acceptorPos = BlockPos(2, 1, 2)
        val rodPos = acceptorPos.east()

        helper.setBlock(acceptorPos, FissionBlocks.FISSION_FUEL_ACCEPTOR.get())
        helper.setBlock(rodPos, CNABlocks.REACTOR_ROD.get())

        val acceptor = helper.getBlockEntity(acceptorPos) as? FissionFuelAcceptorBlockEntity
            ?: error("Expected fission fuel acceptor block entity")
        val uranium = ItemRegistry.getElementByName("uranium").orElseThrow()
        val handler = acceptor.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow {
            IllegalStateException("Expected item handler capability")
        }
        val remainder = handler.insertItem(0, ItemStack(uranium, 16), false)
        helper.assertTrue(remainder.isEmpty, "Uranium stack should insert into the fission acceptor")

        helper.runAfterDelay(60) {
            val updatedAcceptor = helper.getBlockEntity(acceptorPos) as? FissionFuelAcceptorBlockEntity
                ?: error("Expected acceptor block entity after ticking")
            val rod = helper.getBlockEntity(rodPos) as? ReactorRodBlockEntity
                ?: error("Expected reactor rod block entity after ticking")

            helper.assertTrue(updatedAcceptor.getHeat() > 0f, "Acceptor should generate heat from uranium")
            helper.assertTrue(rod.fuel > 0, "Rod should receive fuel from the fission acceptor")
            helper.succeed()
        }
    }
}
