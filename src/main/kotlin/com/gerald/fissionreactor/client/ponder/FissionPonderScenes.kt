package com.gerald.fissionreactor.client.ponder

import com.gerald.fissionreactor.block.FissionFuelAcceptorBlockEntity
import com.gerald.fissionreactor.registry.FissionBlocks
import com.smashingmods.chemlib.registry.ItemRegistry
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import org.antarcticgardens.cna.CNABlocks
import org.antarcticgardens.cna.content.nuclear.reactor.rod.ReactorRodBlockEntity

object FissionPonderScenes {
    fun fueling(scene: SceneBuilder, util: SceneBuildingUtil) {
        val acceptorPos = util.grid().at(2, 1, 2)
        val rodPos = util.grid().at(3, 1, 2)
        val casingPos = util.grid().at(1, 1, 2)
        val uranium = ItemStack(ItemRegistry.getElementByName("uranium").orElseThrow())

        scene.title("fission_fuel_acceptor", "Fueling a fission reactor")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()

        scene.world().showSection(util.select().layer(0), Direction.UP)
        scene.idle(10)
        scene.world().setBlock(casingPos, CNABlocks.REACTOR_CASING.get().defaultBlockState(), false)
        scene.world().setBlock(acceptorPos, FissionBlocks.FISSION_FUEL_ACCEPTOR.get().defaultBlockState(), false)
        scene.world().setBlock(rodPos, CNABlocks.REACTOR_ROD.get().defaultBlockState(), false)
        scene.idle(10)

        scene.overlay().showText(70)
            .text("Fission fuel acceptors replace CNA's fuel acceptor and take ChemLib elements instead.")
            .pointAt(util.vector().topOf(acceptorPos))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showControls(util.vector().topOf(acceptorPos), Pointing.DOWN, 30)
            .rightClick()
            .withItem(uranium)
        scene.overlay().showText(60)
            .colored(PonderPalette.INPUT)
            .text("Only radioactive heavy elements contribute to the neutron economy.")
            .pointAt(util.vector().topOf(acceptorPos))
            .placeNearTarget()
        scene.effects().indicateSuccess(acceptorPos)
        scene.idle(50)

        scene.world().modifyBlockEntity(acceptorPos, FissionFuelAcceptorBlockEntity::class.java) {
            it.setHeat(900f)
        }
        scene.world().modifyBlockEntity(rodPos, ReactorRodBlockEntity::class.java) {
            it.fuel = 32
            it.setHeat(350f)
        }
        scene.overlay().showText(70)
            .colored(PonderPalette.OUTPUT)
            .text("As fuel burns, heat is converted into rod fuel while split fuel stops participating.")
            .pointAt(util.vector().topOf(rodPos))
            .placeNearTarget()
        scene.effects().indicateSuccess(rodPos)
        scene.idle(80)
        scene.markAsFinished()
    }

    fun criticality(scene: SceneBuilder, util: SceneBuildingUtil) {
        val primaryPos = util.grid().at(2, 1, 2)
        val adjacentPos = util.grid().at(1, 1, 2)
        val rodPos = util.grid().at(3, 1, 2)
        val moderatorPos = util.grid().at(2, 1, 1)
        val uranium = ItemStack(ItemRegistry.getElementByName("uranium").orElseThrow())
        val plutonium = ItemStack(ItemRegistry.getElementByName("plutonium").orElseThrow())

        scene.title("fission_criticality", "Managing neutron economy")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()

        scene.world().showSection(util.select().layer(0), Direction.UP)
        scene.idle(10)
        scene.world().setBlock(primaryPos, FissionBlocks.FISSION_FUEL_ACCEPTOR.get().defaultBlockState(), false)
        scene.world().setBlock(adjacentPos, FissionBlocks.FISSION_FUEL_ACCEPTOR.get().defaultBlockState(), false)
        scene.world().setBlock(rodPos, CNABlocks.REACTOR_ROD.get().defaultBlockState(), false)
        scene.idle(10)

        scene.overlay().showControls(util.vector().topOf(primaryPos), Pointing.DOWN, 30)
            .rightClick()
            .withItem(uranium)
        scene.overlay().showControls(util.vector().topOf(adjacentPos), Pointing.DOWN, 30)
            .rightClick()
            .withItem(plutonium)
        scene.overlay().showText(80)
            .text("Adjacent loaded acceptors increase each other's criticality, and heavier fuels push harder.")
            .pointAt(util.vector().topOf(adjacentPos))
            .placeNearTarget()
        scene.effects().indicateSuccess(primaryPos)
        scene.effects().indicateSuccess(adjacentPos)
        scene.idle(90)

        scene.world().modifyBlockEntity(primaryPos, FissionFuelAcceptorBlockEntity::class.java) {
            it.setHeat(3200f)
        }
        scene.world().modifyBlockEntity(adjacentPos, FissionFuelAcceptorBlockEntity::class.java) {
            it.setHeat(5400f)
        }
        scene.world().modifyBlockEntity(rodPos, ReactorRodBlockEntity::class.java) {
            it.fuel = 96
        }
        scene.overlay().showText(70)
            .colored(PonderPalette.RED)
            .text("More active neighbors mean more heat and more radiation pressure on the reactor.")
            .pointAt(util.vector().topOf(primaryPos))
            .placeNearTarget()
        scene.idle(80)

        scene.world().setBlock(moderatorPos, Blocks.COAL_BLOCK.defaultBlockState(), false)
        scene.effects().indicateSuccess(moderatorPos)
        scene.overlay().showText(80)
            .colored(PonderPalette.BLUE)
            .text("Coal or charcoal blocks beside the acceptor act as moderators and reduce criticality.")
            .pointAt(util.vector().topOf(moderatorPos))
            .placeNearTarget()
        scene.idle(90)
        scene.markAsFinished()
    }

    fun meltdown(scene: SceneBuilder, util: SceneBuildingUtil) {
        val acceptorPos = util.grid().at(2, 1, 2)
        val rodPos = util.grid().at(3, 1, 2)
        val shieldA = util.grid().at(1, 1, 1)
        val shieldB = util.grid().at(1, 1, 3)

        scene.title("fission_meltdown", "Overheating, corium, and shielding")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()

        scene.world().showSection(util.select().layer(0), Direction.UP)
        scene.idle(10)
        scene.world().setBlock(acceptorPos, FissionBlocks.FISSION_FUEL_ACCEPTOR.get().defaultBlockState(), false)
        scene.world().setBlock(rodPos, CNABlocks.REACTOR_ROD.get().defaultBlockState(), false)
        scene.world().setBlock(shieldA, CNABlocks.REACTOR_GLASS.get().defaultBlockState(), false)
        scene.world().setBlock(shieldB, CNABlocks.REACTOR_GLASS.get().defaultBlockState(), false)
        scene.idle(10)

        scene.world().modifyBlockEntity(acceptorPos, FissionFuelAcceptorBlockEntity::class.java) {
            it.setHeat(11250f)
        }
        scene.world().modifyBlockEntity(rodPos, ReactorRodBlockEntity::class.java) {
            it.setHeat(2400f)
        }
        scene.overlay().showText(80)
            .colored(PonderPalette.RED)
            .text("If criticality outruns cooling, the acceptor overheats and will melt into corium.")
            .pointAt(util.vector().topOf(acceptorPos))
            .placeNearTarget()
        scene.idle(90)

        scene.world().setBlock(acceptorPos, CNABlocks.CORIUM.get().defaultBlockState(), false)
        scene.effects().indicateRedstone(acceptorPos)
        scene.overlay().showText(80)
            .colored(PonderPalette.OUTPUT)
            .text("Corium is created on meltdown, and the reactor still participates in CNA radiation.")
            .pointAt(util.vector().topOf(acceptorPos))
            .placeNearTarget()
        scene.idle(90)

        scene.overlay().showOutline(PonderPalette.BLUE, "shielding", util.select().fromTo(shieldA, shieldB), 70)
        scene.overlay().showText(80)
            .colored(PonderPalette.BLUE)
            .text("Keep proper CNA shielding around the reactor to protect nearby players from radiation.")
            .pointAt(util.vector().topOf(shieldA))
            .placeNearTarget()
        scene.idle(90)
        scene.markAsFinished()
    }
}
