package com.bettercontent.settlementroads.runtime

import com.bettercontent.settlementroads.planner.PlannerConfig
import com.bettercontent.settlementroads.planner.model.StructureNode
import com.bettercontent.settlementroads.registry.SettlementRoadsRegistryKeys
import com.bettercontent.settlementroads.tag.SettlementRoadsTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk

data class StructureScanResult(
    val discoveredStructures: List<StructureNode>,
    val activeStructures: List<StructureNode>
)

object WorldStructureScanner {
    fun scanLoadedChunks(
        level: ServerLevel,
        loadedChunkKeys: Set<Long>,
        existingStructures: List<StructureNode>,
        config: PlannerConfig = PlannerConfig()
    ): StructureScanResult {
        val structureRegistry = level.registryAccess().registryOrThrow(SettlementRoadsRegistryKeys.STRUCTURES)
        val discoveredById = existingStructures.associateByTo(linkedMapOf()) { it.id }

        for (chunkKey in loadedChunkKeys) {
            val chunk = level.chunkSource.getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) as? LevelChunk ?: continue
            for ((structure, start) in chunk.allStarts) {
                if (!start.isValid) {
                    continue
                }

                val holder = structureRegistry.wrapAsHolder(structure)
                val structureKey = structureRegistry.getKey(structure)?.toString() ?: continue
                val bounds = start.boundingBox
                val center = bounds.center
                val surfaceY = SurfaceSampler.groundY(level, center.x, center.z)
                val surfacePos = BlockPos(center.x, surfaceY, center.z)
                if (!shouldTrackStructure(level, holder.`is`(SettlementRoadsTags.Structures.ROADABLE_STRUCTURES), surfacePos, bounds, config)) {
                    continue
                }

                val sourceChunk = start.chunkPos
                val structureId = "$structureKey@${sourceChunk.x},${sourceChunk.z}"

                discoveredById[structureId] = StructureNode(
                    id = structureId,
                    structureKey = structureKey,
                    center = BlockPos(center.x, maxOf(surfaceY, bounds.minY()), center.z),
                    bounds = bounds,
                    ringPadding = config.defaultRingPadding,
                    clusterRadius = config.defaultClusterRadius,
                    sourceChunkX = sourceChunk.x,
                    sourceChunkZ = sourceChunk.z
                )
            }

        }

        val discoveredStructures = discoveredById.values.sortedBy { it.id }
        val activeStructures = discoveredStructures.filter { structure ->
            val chunkX = structure.sourceChunkX
            val chunkZ = structure.sourceChunkZ
            chunkX == null || chunkZ == null || ChunkPos.asLong(chunkX, chunkZ) in loadedChunkKeys
        }

        return StructureScanResult(
            discoveredStructures = discoveredStructures,
            activeStructures = activeStructures
        )
    }

    private fun shouldTrackStructure(
        level: ServerLevel,
        explicitlyRoadable: Boolean,
        surfacePos: BlockPos,
        bounds: net.minecraft.world.level.levelgen.structure.BoundingBox,
        config: PlannerConfig
    ): Boolean {
        if (!config.scanAllLandStructures) {
            return explicitlyRoadable
        }

        val biome = level.getBiome(surfacePos)
        val surfaceState = level.getBlockState(surfacePos)
        if (biome.`is`(SettlementRoadsTags.Biomes.BRIDGE_FORBIDDEN_BIOMES)) {
            return false
        }
        if (surfaceState.fluidState.`is`(FluidTags.WATER) || surfaceState.fluidState.`is`(FluidTags.LAVA)) {
            return false
        }
        if (bounds.maxY() < surfacePos.y - config.maxLandStructureDepthBelowSurface) {
            return false
        }

        return true
    }
}
