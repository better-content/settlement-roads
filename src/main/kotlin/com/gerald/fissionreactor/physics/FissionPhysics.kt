package com.gerald.fissionreactor.physics

import com.smashingmods.chemlib.api.Element
import kotlin.math.max

object FissionPhysics {
    private const val RADIOACTIVE_THRESHOLD = 84
    private const val BASE_HEAT_DIVISOR = 18.0
    private const val HEAT_PER_GRAM = 45.0
    private const val LOCAL_REACTIVITY_DIVISOR = 96.0
    private const val ADJACENT_REACTIVITY_DIVISOR = 144.0
    private const val MODERATOR_PENALTY = 0.35
    private const val MAX_CRITICALITY = 4.0

    data class FuelProfile(
        val atomicMass: Int,
        val radioactive: Boolean,
        val baseHeat: Double
    ) {
        val participates: Boolean
            get() = baseHeat > 0.0
    }

    fun profile(element: Element): FuelProfile {
        val atomicMass = element.atomicNumber.coerceAtLeast(1)
        val radioactive = element.isArtificial || element.atomicNumber >= RADIOACTIVE_THRESHOLD
        if (!radioactive) {
            return FuelProfile(atomicMass, false, 0.0)
        }

        val weightOverLead = max(0, element.atomicNumber - 82)
        val baseHeat = (weightOverLead * weightOverLead) / BASE_HEAT_DIVISOR
        return FuelProfile(atomicMass, true, baseHeat)
    }

    fun criticalityFactor(
        localReactivity: Double,
        adjacentReactivity: Double,
        moderatorBlocks: Int
    ): Double {
        val raw = 1.0 +
            (localReactivity / LOCAL_REACTIVITY_DIVISOR) +
            (adjacentReactivity / ADJACENT_REACTIVITY_DIVISOR) -
            (moderatorBlocks * MODERATOR_PENALTY)
        return raw.coerceIn(0.0, MAX_CRITICALITY)
    }

    fun heatPerTick(baseHeat: Double, stackCount: Int, criticalityFactor: Double): Double =
        if (baseHeat <= 0.0 || stackCount <= 0 || criticalityFactor <= 0.0) {
            0.0
        } else {
            baseHeat * stackCount * criticalityFactor
        }

    fun massConsumedPerTick(heatPerTick: Double): Double = heatPerTick / HEAT_PER_GRAM

    fun gramsPerItem(element: Element): Int = profile(element).atomicMass

    fun heatPerGram(): Double = HEAT_PER_GRAM
}
