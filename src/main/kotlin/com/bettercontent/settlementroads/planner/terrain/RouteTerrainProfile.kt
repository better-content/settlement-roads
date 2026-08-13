package com.bettercontent.settlementroads.planner.terrain

import com.bettercontent.settlementroads.planner.bridge.SupportMaterialClass
import com.bettercontent.settlementroads.planner.bridge.SupportProbe
import net.minecraft.core.BlockPos

enum class TerrainClass {
    WALKABLE,
    SHALLOW_WATER,
    FORBIDDEN
}

data class TerrainColumn(
    val surfaceY: Int,
    val terrainClass: TerrainClass,
    val supportProbes: List<SupportProbe>
)

data class RouteTerrainProfile(
    val isGrassyBiome: Boolean,
    val defaultSurfaceY: Int,
    val detourOffsetZ: Int = 6,
    val allowDetour: Boolean = true,
    val columns: Map<Pair<Int, Int>, TerrainColumn> = emptyMap()
) {
    fun columnAt(pos: BlockPos): TerrainColumn =
        columns[pos.x to pos.z] ?: defaultColumn(defaultSurfaceY)

    companion object {
        fun defaultColumn(surfaceY: Int): TerrainColumn =
            TerrainColumn(
                surfaceY = surfaceY,
                terrainClass = TerrainClass.WALKABLE,
                supportProbes = listOf(SupportProbe(surfaceY - 1, SupportMaterialClass.SOLID_SUPPORT))
            )

        fun waterColumn(surfaceY: Int, probes: List<SupportProbe>): TerrainColumn =
            TerrainColumn(
                surfaceY = surfaceY,
                terrainClass = TerrainClass.SHALLOW_WATER,
                supportProbes = probes
            )
    }
}
