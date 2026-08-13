package com.bettercontent.settlementroads.command

import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.bridge.SupportMaterialClass
import com.bettercontent.settlementroads.planner.bridge.SupportProbe
import com.bettercontent.settlementroads.planner.model.StructureNode
import com.bettercontent.settlementroads.planner.terrain.RouteTerrainProfile
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.server.level.ServerLevel

data class DebugScenarioDefinition(
    val scenarioId: DebugScenarioId,
    val origin: BlockPos,
    val isGrassy: Boolean,
    val structures: List<StructureNode>,
    val terrainProfile: RouteTerrainProfile,
    val buildBounds: BoundingBox,
    val riverXRange: IntRange? = null,
    val riverHalfWidthZ: Int = 1,
    val caveFloorY: Int? = null
)

object DebugScenarioBuilder {
    fun spawn(level: ServerLevel, definition: DebugScenarioDefinition) {
        clear(level, definition)
        buildTerrain(level, definition)
        placeStructures(level, definition)
    }

    fun clear(level: ServerLevel, definition: DebugScenarioDefinition) {
        for (x in definition.buildBounds.minX()..definition.buildBounds.maxX()) {
            for (z in definition.buildBounds.minZ()..definition.buildBounds.maxZ()) {
                for (y in definition.buildBounds.minY()..definition.buildBounds.maxY()) {
                    level.setBlockAndUpdate(BlockPos(x, y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
    }

    private fun buildTerrain(level: ServerLevel, definition: DebugScenarioDefinition) {
        val surfaceY = definition.origin.y
        val topBlock = if (definition.isGrassy) Blocks.GRASS_BLOCK else Blocks.STONE
        val filler = if (definition.isGrassy) Blocks.DIRT else Blocks.STONE

        for (x in definition.buildBounds.minX()..definition.buildBounds.maxX()) {
            for (z in definition.buildBounds.minZ()..definition.buildBounds.maxZ()) {
                val inRiver = definition.riverXRange?.contains(x) == true &&
                    kotlin.math.abs(z - definition.origin.z) <= definition.riverHalfWidthZ

                for (y in definition.buildBounds.minY() until surfaceY) {
                    val block = when {
                        inRiver && definition.caveFloorY != null && y > definition.caveFloorY -> Blocks.AIR
                        inRiver && y >= surfaceY - 2 -> Blocks.AIR
                        else -> filler
                    }
                    level.setBlockAndUpdate(BlockPos(x, y, z), block.defaultBlockState())
                }

                if (inRiver) {
                    level.setBlockAndUpdate(BlockPos(x, surfaceY, z), Blocks.WATER.defaultBlockState())
                } else {
                    level.setBlockAndUpdate(BlockPos(x, surfaceY, z), topBlock.defaultBlockState())
                }
            }
        }
    }

    private fun placeStructures(level: ServerLevel, definition: DebugScenarioDefinition) {
        for (structure in definition.structures) {
            val bounds = structure.bounds
            for (x in bounds.minX()..bounds.maxX()) {
                for (z in bounds.minZ()..bounds.maxZ()) {
                    for (y in bounds.minY()..bounds.maxY()) {
                        val pos = BlockPos(x, y, z)
                        val block = when {
                            y == bounds.minY() -> Blocks.COBBLESTONE
                            y == bounds.maxY() -> Blocks.OAK_PLANKS
                            x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ() -> Blocks.COBBLESTONE
                            else -> Blocks.AIR
                        }
                        level.setBlockAndUpdate(pos, block.defaultBlockState())
                    }
                }
            }
        }
    }
}

internal fun DebugScenarioId.createDefinition(origin: BlockPos, config: PlannerConfig): DebugScenarioDefinition {
    val y = origin.y

    fun node(id: String, center: BlockPos): StructureNode =
        StructureNode(
            id = id,
            structureKey = "debug/$id",
            center = center,
            bounds = BoundingBox(center.x - 3, y, center.z - 3, center.x + 3, y + 4, center.z + 3),
            ringPadding = config.defaultRingPadding,
            clusterRadius = config.defaultClusterRadius
        )

    val structures = when (this) {
        DebugScenarioId.THREE_STRUCTURE_CLUSTER -> listOf(
            node("alpha", origin.offset(-16, 0, 0)),
            node("beta", origin),
            node("gamma", origin.offset(16, 0, 0))
        )

        DebugScenarioId.CHUNK_BOUNDARY_SPLIT -> listOf(
            node("alpha", BlockPos((origin.x and -16) + 12, y, origin.z)),
            node("beta", BlockPos((origin.x and -16) + 20, y, origin.z))
        )

        DebugScenarioId.TOO_WIDE_RIVER -> listOf(
            node("alpha", origin.offset(-16, 0, 0)),
            node("beta", origin.offset(16, 0, 0))
        )

        else -> listOf(
            node("alpha", origin.offset(-12, 0, 0)),
            node("beta", origin.offset(12, 0, 0))
        )
    }

    val riverXRange = when (this) {
        DebugScenarioId.GRASSY_RIVER_CROSSING,
        DebugScenarioId.ROCKY_CROSSING,
        DebugScenarioId.CAVE_UNDER_RIVERBED -> (origin.x - 2)..(origin.x + 2)

        DebugScenarioId.TOO_WIDE_RIVER -> (origin.x - 7)..(origin.x + 7)
        else -> null
    }

    val buildBounds = BoundingBox(origin.x - 32, y - 8, origin.z - 16, origin.x + 32, y + 10, origin.z + 16)

    val terrainColumns = buildMap<Pair<Int, Int>, com.bettercontent.settlementroads.planner.terrain.TerrainColumn> {
        if (riverXRange != null) {
            for (x in riverXRange) {
                for (z in (origin.z - 1)..(origin.z + 1)) {
                    val probes = when (this@createDefinition) {
                        DebugScenarioId.CAVE_UNDER_RIVERBED -> listOf(
                            SupportProbe(y, SupportMaterialClass.WATER),
                            SupportProbe(y - 1, SupportMaterialClass.AIR),
                            SupportProbe(y - 2, SupportMaterialClass.AIR),
                            SupportProbe(y - 3, SupportMaterialClass.AIR),
                            SupportProbe(y - 4, SupportMaterialClass.AIR),
                            SupportProbe(y - 5, SupportMaterialClass.SOLID_SUPPORT)
                        )

                        else -> listOf(
                            SupportProbe(y, SupportMaterialClass.WATER),
                            SupportProbe(y - 1, SupportMaterialClass.AIR),
                            SupportProbe(y - 2, SupportMaterialClass.SOLID_SUPPORT)
                        )
                    }

                    put(x to z, RouteTerrainProfile.waterColumn(surfaceY = y, probes = probes))
                }
            }
        }
    }

    val terrainProfile = RouteTerrainProfile(
        isGrassyBiome = isGrassy,
        defaultSurfaceY = y,
        detourOffsetZ = 5,
        allowDetour = this != DebugScenarioId.GRASSY_RIVER_CROSSING &&
            this != DebugScenarioId.ROCKY_CROSSING &&
            this != DebugScenarioId.CAVE_UNDER_RIVERBED,
        columns = terrainColumns
    )

    return DebugScenarioDefinition(
        scenarioId = this,
        origin = origin,
        isGrassy = isGrassy,
        structures = structures,
        terrainProfile = terrainProfile,
        buildBounds = buildBounds,
        riverXRange = riverXRange,
        caveFloorY = if (this == DebugScenarioId.CAVE_UNDER_RIVERBED) y - 5 else null
    )
}
