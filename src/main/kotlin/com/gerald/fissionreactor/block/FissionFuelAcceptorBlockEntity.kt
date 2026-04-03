package com.gerald.fissionreactor.block

import com.gerald.fissionreactor.physics.FissionPhysics
import com.gerald.fissionreactor.registry.FissionBlockEntityTypes
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.utility.CreateLang
import com.smashingmods.chemlib.api.Element
import net.createmod.catnip.lang.LangBuilder
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import org.antarcticgardens.cna.CNABlocks
import org.antarcticgardens.cna.config.CNAConfig
import org.antarcticgardens.cna.content.heat.HeatBlockEntity
import org.antarcticgardens.cna.content.nuclear.NuclearUtil
import org.antarcticgardens.cna.content.nuclear.reactor.RodFindingReactorBlockEntity
import org.antarcticgardens.cna.content.nuclear.reactor.rod.ReactorRodBlockEntity
import kotlin.math.floor

class FissionFuelAcceptorBlockEntity(
    pos: BlockPos,
    state: BlockState
) : RodFindingReactorBlockEntity(FissionBlockEntityTypes.FISSION_FUEL_ACCEPTOR.get(), pos, state), HeatBlockEntity, IHaveGoggleInformation {
    private val inventory = object : ItemStackHandler(3) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean = when (slot) {
            OUTPUT_SLOT -> false
            else -> stack.item is Element
        }

        override fun getSlotLimit(slot: Int): Int = 64

        override fun onContentsChanged(slot: Int) {
            setChanged()
        }
    }
    private val capability = LazyOptional.of<IItemHandler> { inventory }

    private var internalHeat = 0f
    private var rodFuelBuffer = 0.0
    private val partialMassConsumption = DoubleArray(2)
    private var lastCriticality = 0.0
    private var lastHeatOutput = 0.0
    private var tickCounter = 0

    override fun invalidateCaps() {
        super.invalidateCaps()
        capability.invalidate()
    }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return capability.cast()
        }
        return super.getCapability(cap, side)
    }

    override fun write(tag: CompoundTag, clientPacket: Boolean) {
        tag.put("inventory", inventory.serializeNBT())
        tag.putFloat("heat", internalHeat)
        tag.putDouble("rodFuelBuffer", rodFuelBuffer)
        tag.putDouble("partialMassConsumption0", partialMassConsumption[0])
        tag.putDouble("partialMassConsumption1", partialMassConsumption[1])
        tag.putDouble("lastCriticality", lastCriticality)
        tag.putDouble("lastHeatOutput", lastHeatOutput)
        super.write(tag, clientPacket)
    }

    override fun read(tag: CompoundTag, clientPacket: Boolean) {
        inventory.deserializeNBT(tag.getCompound("inventory"))
        internalHeat = tag.getFloat("heat")
        rodFuelBuffer = tag.getDouble("rodFuelBuffer")
        partialMassConsumption[0] = tag.getDouble("partialMassConsumption0")
        partialMassConsumption[1] = tag.getDouble("partialMassConsumption1")
        lastCriticality = tag.getDouble("lastCriticality")
        lastHeatOutput = tag.getDouble("lastHeatOutput")
        super.read(tag, clientPacket)
    }

    override fun destroy() {
        super.destroy()
        val currentLevel = level ?: return
        for (slot in 0 until inventory.slots) {
            Containers.dropItemStack(
                currentLevel,
                blockPos.x.toDouble(),
                blockPos.y.toDouble(),
                blockPos.z.toDouble(),
                inventory.getStackInSlot(slot)
            )
        }
    }

    override fun getHeat(): Float = internalHeat

    override fun addHeat(amount: Float) {
        internalHeat += amount
        setChanged()
    }

    override fun setHeat(amount: Float) {
        internalHeat = amount
        setChanged()
    }

    override fun maxHeat(): Float = 12000f

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        HeatBlockEntity.addToolTips(this, tooltip)
        lang("tooltip.fission_reactor.criticality", "%.2f".format(lastCriticality)).style(ChatFormatting.GOLD).forGoggles(tooltip, 1)
        lang("tooltip.fission_reactor.heat_output", "%.2f".format(lastHeatOutput)).style(ChatFormatting.RED).forGoggles(tooltip, 1)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun tickServer(pos: BlockPos, level: Level, state: BlockState) {
        tickCounter++
        if (tickCounter % 20 != 0) {
            return
        }

        HeatBlockEntity.transferAround(this)
        internalHeat = (internalHeat - 18f).coerceAtLeast(0f)

        val rods = mutableListOf<ReactorRodBlockEntity>()
        Direction.values().forEach { findRods(rods, it) }
        if (rods.isEmpty()) {
            lastCriticality = 0.0
            lastHeatOutput = 0.0
            setChanged()
            return
        }

        val moderators = countModeratorBlocks()
        val localReactivity = totalReactivity()
        val adjacentReactivity = adjacentReactivity()
        val criticality = FissionPhysics.criticalityFactor(localReactivity, adjacentReactivity, moderators)
        lastCriticality = criticality

        var producedHeatPerTick = 0.0
        for (slot in INPUT_SLOTS) {
            val stack = inventory.getStackInSlot(slot)
            val element = stack.item as? Element ?: continue
            val profile = FissionPhysics.profile(element)
            if (!profile.participates) {
                continue
            }

            val heatPerTick = FissionPhysics.heatPerTick(profile.baseHeat, stack.count, criticality)
            if (heatPerTick <= 0.0) {
                continue
            }

            producedHeatPerTick += heatPerTick
            partialMassConsumption[slot] += FissionPhysics.massConsumedPerTick(heatPerTick) * 20.0
            splitFuelIfNeeded(slot, stack, element)
        }

        lastHeatOutput = producedHeatPerTick
        val producedHeatPerSecond = producedHeatPerTick * 20.0
        rodFuelBuffer += producedHeatPerSecond
        internalHeat += (producedHeatPerSecond * 0.12).toFloat()

        val rodFuelUnitHeat = (CNAConfig.getCommon().nuclearReactorRodHeat.get() as Double).coerceAtLeast(1.0)
        val fuelUnits = floor(rodFuelBuffer / rodFuelUnitHeat).toInt()
        if (fuelUnits > 0) {
            rodFuelBuffer -= fuelUnits * rodFuelUnitHeat
            val perRod = fuelUnits / rods.size
            val remainder = fuelUnits % rods.size
            rods.forEachIndexed { index, rod ->
                rod.fuel += perRod + if (index < remainder) 1 else 0
                rod.setChanged()
            }
        }

        if (producedHeatPerTick > 0.0) {
            NuclearUtil.createRadiation((2.0 + criticality).toInt().coerceAtLeast(1), level, pos)
        }

        HeatBlockEntity.handleOverheat(this) { meltDown(level, pos) }
        if (internalHeat > maxHeat()) {
            meltDown(level, pos)
        } else {
            setChanged()
        }
    }

    private fun splitFuelIfNeeded(slot: Int, stack: ItemStack, element: Element) {
        val gramsPerItem = FissionPhysics.gramsPerItem(element).toDouble()
        while (partialMassConsumption[slot] >= gramsPerItem && !stack.isEmpty) {
            if (!canOutput(stack)) {
                partialMassConsumption[slot] = gramsPerItem
                return
            }

            partialMassConsumption[slot] -= gramsPerItem
            inventory.extractItem(slot, 1, false)
            val remainder = inventory.insertItem(OUTPUT_SLOT, ItemStack(stack.item, 1), false)
            if (!remainder.isEmpty) {
                inventory.insertItem(slot, ItemStack(stack.item, 1), false)
                partialMassConsumption[slot] = gramsPerItem
                return
            }
        }
    }

    private fun canOutput(stack: ItemStack): Boolean {
        val output = inventory.getStackInSlot(OUTPUT_SLOT)
        return output.isEmpty || (ItemStack.isSameItemSameTags(output, stack) && output.count < output.maxStackSize)
    }

    private fun totalReactivity(): Double =
        INPUT_SLOTS.sumOf { slot ->
            val stack = inventory.getStackInSlot(slot)
            val element = stack.item as? Element ?: return@sumOf 0.0
            val profile = FissionPhysics.profile(element)
            profile.baseHeat * stack.count
        }

    private fun adjacentReactivity(): Double =
        Direction.values().sumOf { direction ->
            val adjacent = level?.getBlockEntity(blockPos.relative(direction)) as? FissionFuelAcceptorBlockEntity ?: return@sumOf 0.0
            adjacent.totalReactivity()
        }

    private fun countModeratorBlocks(): Int =
        Direction.values().count { direction ->
            val block = level?.getBlockState(blockPos.relative(direction))?.block
            block == Blocks.COAL_BLOCK || block?.asItem()?.let { item ->
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item)?.path == "charcoal_block"
            } == true
        }

    private fun meltDown(level: Level, pos: BlockPos) {
        for (slot in 0 until inventory.slots) {
            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, inventory.getStackInSlot(slot))
        }
        level.setBlock(pos, CNABlocks.CORIUM.getDefaultState(), Block.UPDATE_ALL)
    }

    private fun lang(key: String, value: String): LangBuilder = CreateLang.translate(key, value)

    companion object {
        private const val OUTPUT_SLOT = 2
        private val INPUT_SLOTS = 0..1

        fun tickServer(level: Level, pos: BlockPos, state: BlockState, blockEntity: FissionFuelAcceptorBlockEntity) {
            if (!level.isClientSide) {
                blockEntity.tickServer(pos, level, state)
            }
        }
    }
}
