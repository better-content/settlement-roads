package com.bettercontent.settlementroads.worldgen

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration

class TestLandmarkFeature : Feature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC) {
    override fun place(context: FeaturePlaceContext<NoneFeatureConfiguration>): Boolean {
        val level = context.level()
        val random = context.random()
        val surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, context.origin())
        val base = surface.below()
        val baseState = level.getBlockState(base)

        if (surface.y <= level.minBuildHeight + 1) {
            return false
        }
        if (baseState.fluidState.`is`(FluidTags.WATER) || baseState.fluidState.`is`(FluidTags.LAVA)) {
            return false
        }
        if (!baseState.isFaceSturdy(level, base, Direction.UP)) {
            return false
        }

        when (random.nextInt(3)) {
            0 -> placeBox(level, surface)
            1 -> placeTower(level, surface)
            else -> placeArch(level, surface)
        }

        level.setBlock(surface, Blocks.MAGENTA_CONCRETE.defaultBlockState(), 3)
        return true
    }

    private fun placeBox(level: WorldGenLevel, origin: BlockPos) {
        fill(level, origin.offset(-2, 0, -2), origin.offset(2, 0, 2), Blocks.YELLOW_CONCRETE.defaultBlockState())
        fillHollow(level, origin.offset(-2, 1, -2), origin.offset(2, 3, 2), Blocks.LIME_CONCRETE.defaultBlockState())
        fill(level, origin.offset(-2, 4, -2), origin.offset(2, 4, 2), Blocks.WHITE_CONCRETE.defaultBlockState())
        carveDoor(level, origin.offset(0, 1, -2), 2)
    }

    private fun placeTower(level: WorldGenLevel, origin: BlockPos) {
        fill(level, origin.offset(-1, 0, -1), origin.offset(1, 0, 1), Blocks.ORANGE_CONCRETE.defaultBlockState())
        fillHollow(level, origin.offset(-1, 1, -1), origin.offset(1, 7, 1), Blocks.CYAN_CONCRETE.defaultBlockState())
        fill(level, origin.offset(-1, 8, -1), origin.offset(1, 8, 1), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState())
        carveDoor(level, origin.offset(0, 1, -1), 2)
    }

    private fun placeArch(level: WorldGenLevel, origin: BlockPos) {
        fill(level, origin.offset(-3, 0, -1), origin.offset(3, 0, 1), Blocks.RED_CONCRETE.defaultBlockState())
        fill(level, origin.offset(-3, 1, -1), origin.offset(-2, 4, 1), Blocks.BLUE_CONCRETE.defaultBlockState())
        fill(level, origin.offset(2, 1, -1), origin.offset(3, 4, 1), Blocks.BLUE_CONCRETE.defaultBlockState())
        fill(level, origin.offset(-1, 4, -1), origin.offset(1, 5, 1), Blocks.GREEN_CONCRETE.defaultBlockState())
        carveAir(level, origin.offset(-1, 1, -1), origin.offset(1, 3, 1))
        level.setBlock(origin.above(6), Blocks.GLOWSTONE.defaultBlockState(), 3)
    }

    private fun fillHollow(level: WorldGenLevel, min: BlockPos, max: BlockPos, state: BlockState) {
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    val isShell = x == min.x || x == max.x || y == min.y || y == max.y || z == min.z || z == max.z
                    if (isShell) {
                        level.setBlock(BlockPos(x, y, z), state, 3)
                    }
                }
            }
        }
    }

    private fun fill(level: WorldGenLevel, min: BlockPos, max: BlockPos, state: BlockState) {
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    level.setBlock(BlockPos(x, y, z), state, 3)
                }
            }
        }
    }

    private fun carveDoor(level: WorldGenLevel, bottom: BlockPos, height: Int) {
        carveAir(level, bottom, bottom.above(height - 1))
    }

    private fun carveAir(level: WorldGenLevel, min: BlockPos, max: BlockPos) {
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3)
                }
            }
        }
    }
}
