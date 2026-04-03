package com.gerald.settlementroads.planner.palette

import net.minecraft.core.BlockPos

enum class SurfacePalette(val mainBlockId: String) {
    GRASSY("minecraft:dirt_path"),
    NON_GRASSY("minecraft:gravel")
}

object SurfacePaletteSelector {
    fun choose(isGrassyBiome: Boolean): SurfacePalette =
        if (isGrassyBiome) SurfacePalette.GRASSY else SurfacePalette.NON_GRASSY

    fun chooseCoarseDirtDetail(
        pathFootprint: Set<BlockPos>,
        centerline: Set<BlockPos>,
        seed: Long,
        detailRate: Double
    ): Set<BlockPos> {
        if (detailRate <= 0.0 || pathFootprint.isEmpty()) {
            return emptySet()
        }

        return pathFootprint
            .asSequence()
            .filter { it !in centerline }
            .filter { stableChance(it, seed) < detailRate }
            .toSet()
    }

    private fun stableChance(pos: BlockPos, seed: Long): Double {
        var hash = seed
        hash = hash * 6364136223846793005L + pos.x.toLong() * 1442695040888963407L
        hash = hash xor (pos.y.toLong() * 22695477L)
        hash = hash * 3202034522624059733L + pos.z.toLong() * 3935559000370003845L
        hash = (hash xor (hash ushr 30)) * 2862933555777941757L
        hash = (hash xor (hash ushr 27)) * 3037000493L
        hash = hash xor (hash ushr 31)
        val normalized = hash and Long.MAX_VALUE
        return normalized.toDouble() / Long.MAX_VALUE.toDouble()
    }
}
