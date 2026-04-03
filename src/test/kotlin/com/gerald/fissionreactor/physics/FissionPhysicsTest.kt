package com.gerald.fissionreactor.physics

import com.smashingmods.chemlib.api.Element
import com.smashingmods.chemlib.api.MatterState
import com.smashingmods.chemlib.api.MetalType
import net.minecraft.world.effect.MobEffectInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FissionPhysicsTest {
    @Test
    fun nonRadioactiveElementsDoNotParticipate() {
        val carbon = fakeElement("carbon", 6, false)
        val profile = FissionPhysics.profile(carbon)
        assertFalse(profile.participates)
        assertEquals(0.0, profile.baseHeat)
    }

    @Test
    fun heavierRadioactiveElementsRunHotter() {
        val thorium = fakeElement("thorium", 90, false)
        val uranium = fakeElement("uranium", 92, false)
        val californium = fakeElement("californium", 98, true)

        assertTrue(FissionPhysics.profile(uranium).baseHeat > FissionPhysics.profile(thorium).baseHeat)
        assertTrue(FissionPhysics.profile(californium).baseHeat > FissionPhysics.profile(uranium).baseHeat)
    }

    @Test
    fun moderatorsClampCriticality() {
        val hot = FissionPhysics.criticalityFactor(localReactivity = 256.0, adjacentReactivity = 128.0, moderatorBlocks = 0)
        val moderated = FissionPhysics.criticalityFactor(localReactivity = 256.0, adjacentReactivity = 128.0, moderatorBlocks = 4)

        assertTrue(hot > moderated)
        assertTrue(moderated >= 0.0)
    }

    @Test
    fun heatMapsToMassConsumption() {
        val perTick = FissionPhysics.heatPerTick(baseHeat = 10.0, stackCount = 4, criticalityFactor = 1.5)
        assertEquals(60.0, perTick)
        assertEquals(60.0 / FissionPhysics.heatPerGram(), FissionPhysics.massConsumedPerTick(perTick))
    }

    private fun fakeElement(name: String, atomicNumber: Int, artificial: Boolean): Element = object : Element {
        override fun getAtomicNumber(): Int = atomicNumber
        override fun getGroup(): Int = 3
        override fun getPeriod(): Int = 7
        override fun getMetalType(): MetalType = MetalType.METAL
        override fun isArtificial(): Boolean = artificial
        override fun getGroupName(): String = "actinides"
        override fun getChemicalName(): String = name
        override fun getAbbreviation(): String = name.take(2)
        override fun getMatterState(): MatterState = MatterState.SOLID
        override fun getChemicalDescription(): String = name
        override fun getEffects(): MutableList<MobEffectInstance> = mutableListOf()
        override fun getColor(): Int = 0xffffff
        override fun asItem() = throw UnsupportedOperationException()
    }
}
