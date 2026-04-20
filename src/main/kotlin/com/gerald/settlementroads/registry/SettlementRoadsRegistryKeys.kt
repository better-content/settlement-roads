package com.gerald.settlementroads.registry

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.structure.Structure

object SettlementRoadsRegistryKeys {
    val BLOCKS: ResourceKey<Registry<Block>> = create("block")
    val BIOMES: ResourceKey<Registry<Biome>> = create("worldgen/biome")
    val FEATURES: ResourceKey<Registry<Feature<*>>> = create("worldgen/feature")
    val STRUCTURES: ResourceKey<Registry<Structure>> = create("worldgen/structure")

    private fun <T> create(path: String): ResourceKey<Registry<T>> =
        ResourceKey.createRegistryKey(ResourceLocation("minecraft", path))
}
