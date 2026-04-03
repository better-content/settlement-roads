package com.gerald.settlementroads.tag

import com.gerald.settlementroads.SettlementRoadsMod
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.Structure

object SettlementRoadsTags {
    object Blocks {
        val SOLID_BRIDGE_SUPPORT_BLOCKS: TagKey<Block> = blockTag("solid_bridge_support_blocks")
        val SOFT_BRIDGE_SUPPORT_BLOCKS: TagKey<Block> = blockTag("soft_bridge_support_blocks")
        val BRIDGE_FORBIDDEN_BLOCKS: TagKey<Block> = blockTag("bridge_forbidden_blocks")
    }

    object Biomes {
        val GRASSY_BIOMES: TagKey<Biome> = biomeTag("grassy_biomes")
        val NON_GRASSY_BIOMES: TagKey<Biome> = biomeTag("non_grassy_biomes")
        val BRIDGE_FORBIDDEN_BIOMES: TagKey<Biome> = biomeTag("bridge_forbidden_biomes")
    }

    object Structures {
        val ROADABLE_STRUCTURES: TagKey<Structure> = structureTag("roadable_structures")
    }

    private fun blockTag(path: String): TagKey<Block> =
        TagKey.create(Registries.BLOCK, id(path))

    private fun biomeTag(path: String): TagKey<Biome> =
        TagKey.create(Registries.BIOME, id(path))

    private fun structureTag(path: String): TagKey<Structure> =
        TagKey.create(Registries.STRUCTURE, id(path))

    private fun id(path: String): ResourceLocation =
        ResourceLocation(SettlementRoadsMod.MOD_ID, path)
}
