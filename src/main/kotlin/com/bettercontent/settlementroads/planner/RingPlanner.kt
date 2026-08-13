package com.bettercontent.settlementroads.planner

import com.bettercontent.settlementroads.planner.model.RingPath
import com.bettercontent.settlementroads.planner.model.StructureNode
import net.minecraft.core.BlockPos
import kotlin.math.max

object RingPlanner {
    fun plan(structure: StructureNode): RingPath {
        val padding = max(1, structure.ringPadding)
        val minX = structure.bounds.minX() - padding
        val maxX = structure.bounds.maxX() + padding
        val minZ = structure.bounds.minZ() - padding
        val maxZ = structure.bounds.maxZ() + padding
        val y = structure.center.y

        val perimeter = mutableListOf<BlockPos>()

        for (x in minX..maxX) {
            perimeter.add(BlockPos(x, y, minZ))
        }
        for (z in (minZ + 1)..maxZ) {
            perimeter.add(BlockPos(maxX, y, z))
        }
        for (x in (maxX - 1) downTo minX) {
            perimeter.add(BlockPos(x, y, maxZ))
        }
        for (z in (maxZ - 1) downTo (minZ + 1)) {
            perimeter.add(BlockPos(minX, y, z))
        }

        if (perimeter.isNotEmpty()) {
            perimeter.add(perimeter.first())
        }

        return RingPath(structureId = structure.id, perimeter = perimeter)
    }

    fun chooseAnchor(ring: RingPath, target: BlockPos): BlockPos =
        ring.perimeter
            .dropLast(1)
            .minWith(
                compareBy<BlockPos> { horizontalDistanceSquared(it, target) }
                    .thenBy { it.x }
                    .thenBy { it.z }
            )

    private fun horizontalDistanceSquared(left: BlockPos, right: BlockPos): Long {
        val dx = (left.x - right.x).toLong()
        val dz = (left.z - right.z).toLong()
        return dx * dx + dz * dz
    }
}
