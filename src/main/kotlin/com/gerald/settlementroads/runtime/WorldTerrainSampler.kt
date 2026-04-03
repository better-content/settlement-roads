package com.gerald.settlementroads.runtime

import com.gerald.settlementroads.planner.PlannerConfig
import com.gerald.settlementroads.planner.bridge.SupportMaterialClass
import com.gerald.settlementroads.planner.bridge.SupportProbe
import com.gerald.settlementroads.planner.terrain.RouteTerrainProfile
import com.gerald.settlementroads.planner.terrain.TerrainClass
import com.gerald.settlementroads.planner.terrain.TerrainColumn
import com.gerald.settlementroads.tag.SettlementRoadsTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState

object WorldTerrainSampler {
    fun sample(level: ServerLevel, start: BlockPos, end: BlockPos, config: PlannerConfig): RouteTerrainProfile {
        val minX = minOf(start.x, end.x) - config.routeSearchMargin
        val maxX = maxOf(start.x, end.x) + config.routeSearchMargin
        val minZ = minOf(start.z, end.z) - config.routeSearchMargin
        val maxZ = maxOf(start.z, end.z) + config.routeSearchMargin
        val columns = linkedMapOf<Pair<Int, Int>, TerrainColumn>()

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val chunk = level.chunkSource.getChunkNow(x shr 4, z shr 4)
                if (chunk == null) {
                    columns[x to z] = TerrainColumn(
                        surfaceY = level.minBuildHeight,
                        terrainClass = TerrainClass.FORBIDDEN,
                        supportProbes = emptyList()
                    )
                    continue
                }

                val topY = SurfaceSampler.groundY(level, x, z)
                val pos = BlockPos(x, topY, z)
                val state = level.getBlockState(pos)
                val fluidState = state.fluidState

                columns[x to z] = TerrainColumn(
                    surfaceY = topY,
                    terrainClass = when {
                        state.`is`(SettlementRoadsTags.Blocks.BRIDGE_FORBIDDEN_BLOCKS) || fluidState.`is`(FluidTags.LAVA) -> TerrainClass.FORBIDDEN
                        fluidState.`is`(FluidTags.WATER) -> TerrainClass.SHALLOW_WATER
                        else -> TerrainClass.WALKABLE
                    },
                    supportProbes = sampleSupportColumn(level, x, z, topY, config)
                )
            }
        }

        val isGrassyBiome = level.getBiome(start).`is`(SettlementRoadsTags.Biomes.GRASSY_BIOMES) ||
            !level.getBiome(start).`is`(SettlementRoadsTags.Biomes.NON_GRASSY_BIOMES)

        return RouteTerrainProfile(
            isGrassyBiome = isGrassyBiome,
            defaultSurfaceY = SurfaceSampler.groundY(level, start.x, start.z),
            detourOffsetZ = config.routeSearchMargin / 2,
            allowDetour = true,
            columns = columns
        )
    }

    private fun sampleSupportColumn(
        level: ServerLevel,
        x: Int,
        z: Int,
        topY: Int,
        config: PlannerConfig
    ): List<SupportProbe> {
        val probes = mutableListOf<SupportProbe>()
        val minY = maxOf(level.minBuildHeight, topY - config.maxSupportProbeDepth)

        for (y in topY downTo minY) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            val material = classifySupportMaterial(level, pos, state)
            probes += SupportProbe(
                y = y,
                material = material,
                uneven = material == SupportMaterialClass.SOFT_SUPPORT && hasUnevenNeighbor(level, x, y, z)
            )

            if (material == SupportMaterialClass.SOFT_SUPPORT || material == SupportMaterialClass.SOLID_SUPPORT) {
                break
            }
        }

        return probes
    }

    private fun classifySupportMaterial(level: ServerLevel, pos: BlockPos, state: BlockState): SupportMaterialClass {
        if (state.isAir) {
            return SupportMaterialClass.AIR
        }
        if (state.fluidState.`is`(FluidTags.WATER)) {
            return SupportMaterialClass.WATER
        }
        if (state.`is`(BlockTags.LOGS)) {
            return SupportMaterialClass.LOG
        }
        if (state.`is`(BlockTags.LEAVES)) {
            return SupportMaterialClass.LEAVES
        }
        if (state.`is`(SettlementRoadsTags.Blocks.SOFT_BRIDGE_SUPPORT_BLOCKS)) {
            return SupportMaterialClass.SOFT_SUPPORT
        }
        if (state.`is`(SettlementRoadsTags.Blocks.SOLID_BRIDGE_SUPPORT_BLOCKS)) {
            return SupportMaterialClass.SOLID_SUPPORT
        }
        if (state.isFaceSturdy(level, pos, Direction.UP) || state.isCollisionShapeFullBlock(level, pos)) {
            return SupportMaterialClass.SOLID_SUPPORT
        }
        return SupportMaterialClass.PLANT
    }

    private fun hasUnevenNeighbor(level: ServerLevel, x: Int, y: Int, z: Int): Boolean {
        val center = BlockPos(x, y, z)
        val neighbors = listOf(
            center.north(),
            center.south(),
            center.east(),
            center.west()
        )

        return neighbors.any { neighbor ->
            val chunkKey = ChunkPos.asLong(neighbor.x shr 4, neighbor.z shr 4)
            level.chunkSource.hasChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) &&
                level.getBlockState(neighbor).isAir
        }
    }
}
