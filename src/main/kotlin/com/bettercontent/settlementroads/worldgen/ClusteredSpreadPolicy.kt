package com.bettercontent.settlementroads.worldgen

import kotlin.math.sqrt
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure deterministic placement field shared by clustered structure sets.
 * A common cluster salt puts nearby sets around the same centres; each set's
 * ordinary placement salt gives it an independent site roll.
 */
object ClusteredSpreadPolicy {
    data class Site(val chunkX: Int, val chunkZ: Int, val probability: Double)
    data class Center(val chunkX: Int, val chunkZ: Int)
    private data class KernelKey(val spacing: Int, val radius: Int, val localCenterX: Int, val localCenterZ: Int)
    private val kernelSums = ConcurrentHashMap<KernelKey, Double>()
    private const val MAX_CACHED_KERNELS = 65_536

    fun center(levelSeed: Long, chunkX: Int, chunkZ: Int, clusterSalt: Int, spacing: Int): Center {
        require(spacing > 0)
        val cellX = Math.floorDiv(chunkX, spacing)
        val cellZ = Math.floorDiv(chunkZ, spacing)
        val cellMinX = cellX * spacing
        val cellMinZ = cellZ * spacing
        return Center(
            cellMinX + bounded(mix(levelSeed, clusterSalt, cellX, cellZ, 0), spacing),
            cellMinZ + bounded(mix(levelSeed, clusterSalt, cellX, cellZ, 1), spacing)
        )
    }

    fun site(
        levelSeed: Long,
        chunkX: Int,
        chunkZ: Int,
        placementSalt: Int,
        clusterSalt: Int,
        spacing: Int,
        radius: Int,
        baselineSpacing: Int,
        sitesPerCluster: Int = 1
    ): Site? {
        require(spacing > 0 && radius >= 8 && radius * 2 < spacing)
        require(baselineSpacing >= spacing)
        require(sitesPerCluster > 0)

        val cellX = Math.floorDiv(chunkX, spacing)
        val cellZ = Math.floorDiv(chunkZ, spacing)
        val cellMinX = cellX * spacing
        val cellMinZ = cellZ * spacing
        if (!isClusterActive(levelSeed, chunkX, chunkZ, clusterSalt, spacing, baselineSpacing, sitesPerCluster)) return null
        val center = center(levelSeed, chunkX, chunkZ, clusterSalt, spacing)
        val probability = probability(chunkX, chunkZ, center.chunkX, center.chunkZ, cellMinX, cellMinZ, spacing, radius, baselineSpacing, sitesPerCluster)
        if (probability <= 0.0) return null
        if (unit(mix(levelSeed, placementSalt, chunkX, chunkZ, 2)) >= probability) return null
        return Site(chunkX, chunkZ, probability)
    }

    fun probability(
        chunkX: Int,
        chunkZ: Int,
        centerX: Int,
        centerZ: Int,
        cellMinX: Int,
        cellMinZ: Int,
        spacing: Int,
        radius: Int,
        baselineSpacing: Int,
        sitesPerCluster: Int = 1
    ): Double {
        require(spacing > 0 && radius >= 8 && radius * 2 < spacing)
        require(baselineSpacing >= spacing)
        require(sitesPerCluster > 0)
        val dx = chunkX - centerX
        val dz = chunkZ - centerZ
        val distanceSquared = dx.toLong() * dx + dz.toLong() * dz
        if (distanceSquared >= radius.toLong() * radius) return 0.0
        val weightSum = weightSum(centerX, centerZ, cellMinX, cellMinZ, spacing, radius)
        require(sitesPerCluster <= weightSum) { "cluster site budget exceeds available radial kernel mass" }
        val falloff = 1.0 - sqrt(distanceSquared.toDouble()) / radius
        val weight = falloff * falloff
        return sitesPerCluster * weight / weightSum
    }

    fun activationProbability(spacing: Int, baselineSpacing: Int, sitesPerCluster: Int): Double {
        require(spacing > 0 && baselineSpacing >= spacing && sitesPerCluster > 0)
        val expectedSitesPerCell = spacing.toDouble() * spacing / (baselineSpacing.toDouble() * baselineSpacing)
        return expectedSitesPerCell / sitesPerCluster
    }

    fun isClusterActive(levelSeed: Long, chunkX: Int, chunkZ: Int, clusterSalt: Int, spacing: Int, baselineSpacing: Int, sitesPerCluster: Int): Boolean {
        require(spacing > 0 && baselineSpacing >= spacing && sitesPerCluster > 0)
        val cellX = Math.floorDiv(chunkX, spacing)
        val cellZ = Math.floorDiv(chunkZ, spacing)
        return unit(mix(levelSeed, clusterSalt, cellX, cellZ, 1)) < activationProbability(spacing, baselineSpacing, sitesPerCluster)
    }

    /** Sum of the radial kernel over valid candidate chunks in this macro-cell. */
    fun weightSum(centerX: Int, centerZ: Int, cellMinX: Int, cellMinZ: Int, spacing: Int, radius: Int): Double {
        val key = KernelKey(spacing, radius, centerX - cellMinX, centerZ - cellMinZ)
        kernelSums[key]?.let { return it }
        val calculated = calculateWeightSum(centerX, centerZ, cellMinX, cellMinZ, spacing, radius)
        if (kernelSums.size < MAX_CACHED_KERNELS) return kernelSums.putIfAbsent(key, calculated) ?: calculated
        return calculated
    }

    private fun calculateWeightSum(centerX: Int, centerZ: Int, cellMinX: Int, cellMinZ: Int, spacing: Int, radius: Int): Double {
        var total = 0.0
        val minX = maxOf(cellMinX, centerX - radius + 1)
        val maxX = minOf(cellMinX + spacing - 1, centerX + radius - 1)
        val minZ = maxOf(cellMinZ, centerZ - radius + 1)
        val maxZ = minOf(cellMinZ + spacing - 1, centerZ + radius - 1)
        for (x in minX..maxX) for (z in minZ..maxZ) {
            val dx = x - centerX
            val dz = z - centerZ
            val distance = sqrt((dx.toLong() * dx + dz.toLong() * dz).toDouble())
            if (distance < radius) {
                val falloff = 1.0 - distance / radius
                total += falloff * falloff
            }
        }
        return total
    }

    private fun bounded(value: Long, bound: Int): Int = java.lang.Long.remainderUnsigned(value, bound.toLong()).toInt()

    private fun unit(value: Long): Double = (value ushr 11).toDouble() / 9_007_199_254_740_992.0

    private fun mix(seed: Long, salt: Int, x: Int, z: Int, stream: Int): Long {
        var value = seed xor (salt.toLong() shl 32) xor (x.toLong() * -7046029254386353131L)
        value = value xor (z.toLong() * -4417276706812531889L) xor (stream.toLong() * -7723592293110705685L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
