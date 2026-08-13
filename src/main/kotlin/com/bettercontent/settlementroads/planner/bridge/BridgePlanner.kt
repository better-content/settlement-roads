package com.bettercontent.settlementroads.planner.bridge

import com.bettercontent.settlementroads.planner.model.SupportColumn

enum class SupportMaterialClass {
    AIR,
    WATER,
    PLANT,
    LOG,
    LEAVES,
    SOFT_SUPPORT,
    SOLID_SUPPORT
}

data class SupportProbe(
    val y: Int,
    val material: SupportMaterialClass,
    val uneven: Boolean = false
)

sealed class BridgeDecision {
    data class Accepted(val bridgeCost: Int) : BridgeDecision()
    data class Rejected(val reason: String) : BridgeDecision()
}

object BridgePlanner {
    fun evaluateSpan(
        waterSpan: Int,
        rerouteCost: Int,
        maxSpan: Int,
        bankGrade: Int,
        maxBankGrade: Int,
        bridgeCostPerBlock: Int
    ): BridgeDecision {
        if (waterSpan > maxSpan) {
            return BridgeDecision.Rejected("span_too_wide")
        }
        if (bankGrade > maxBankGrade) {
            return BridgeDecision.Rejected("bank_grade_too_steep")
        }

        val bridgeCost = waterSpan * bridgeCostPerBlock
        return if (bridgeCost < rerouteCost) {
            BridgeDecision.Accepted(bridgeCost)
        } else {
            BridgeDecision.Rejected("reroute_cheaper")
        }
    }

    fun descendSupport(x: Int, z: Int, fromY: Int, probes: List<SupportProbe>): SupportColumn {
        val ordered = probes.sortedByDescending { it.y }
        val terminal = ordered.firstOrNull { it.material == SupportMaterialClass.SOFT_SUPPORT || it.material == SupportMaterialClass.SOLID_SUPPORT }

        if (terminal == null) {
            return SupportColumn(
                x = x,
                z = z,
                fromY = fromY,
                toY = fromY,
                baseWidth = 1,
                reachedSolid = false
            )
        }

        val baseWidth = when {
            terminal.material == SupportMaterialClass.SOFT_SUPPORT -> 2
            terminal.uneven -> 2
            else -> 1
        }

        return SupportColumn(
            x = x,
            z = z,
            fromY = fromY,
            toY = terminal.y,
            baseWidth = baseWidth,
            reachedSolid = true
        )
    }

    fun midPierOffsets(spanLength: Int, pierSpacing: Int): List<Int> {
        if (pierSpacing <= 0 || spanLength <= pierSpacing) {
            return emptyList()
        }

        return generateSequence(pierSpacing) { it + pierSpacing }
            .takeWhile { it < spanLength }
            .toList()
    }
}
