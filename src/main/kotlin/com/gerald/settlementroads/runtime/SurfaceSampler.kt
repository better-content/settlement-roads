package com.gerald.settlementroads.runtime

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap

object SurfaceSampler {
    fun groundY(level: ServerLevel, x: Int, z: Int): Int =
        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1

    fun groundPos(level: ServerLevel, x: Int, z: Int): BlockPos =
        BlockPos(x, groundY(level, x, z), z)
}
