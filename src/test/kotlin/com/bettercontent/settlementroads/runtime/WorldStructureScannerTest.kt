package com.bettercontent.settlementroads.runtime

import com.bettercontent.settlementroads.planner.model.StructureNode
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldStructureScannerTest {
    @Test
    fun legacy_test_landmarks_are_retained_but_not_production_structures() {
        val legacy = node("settlement_roads:test_landmark@0,64,0", "settlement_roads:test_landmark")
        val village = node("minecraft:village_plains@0,0", "minecraft:village_plains")

        assertFalse(WorldStructureScanner.isProductionStructure(legacy))
        assertTrue(WorldStructureScanner.isProductionStructure(village))
    }

    private fun node(id: String, structureKey: String) = StructureNode(
        id = id,
        structureKey = structureKey,
        center = BlockPos.ZERO,
        bounds = BoundingBox(0, 64, 0, 1, 65, 1),
        ringPadding = 4,
        clusterRadius = 64,
        sourceChunkX = 0,
        sourceChunkZ = 0
    )
}
