package com.gerald.settlementroads.planner

import com.gerald.settlementroads.planner.bridge.SupportMaterialClass
import com.gerald.settlementroads.planner.bridge.SupportProbe
import com.gerald.settlementroads.planner.model.PathSegment
import com.gerald.settlementroads.planner.model.RingPath
import com.gerald.settlementroads.planner.model.StructureNode
import com.gerald.settlementroads.planner.terrain.RouteTerrainProfile
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoutePlannerTest {
    private val config = PlannerConfig()

    @Test
    fun route_uses_bridge_when_short_span_is_cheaper_than_detour() {
        val structures = twinStructures()
        val rings = structures.associate { it.id to RingPlanner.plan(it) }
        val terrain = RouteTerrainProfile(
            isGrassyBiome = true,
            defaultSurfaceY = 64,
            allowDetour = false,
            columns = (-2..2).associate { x ->
                (x to 0) to RouteTerrainProfile.waterColumn(
                    surfaceY = 64,
                    probes = listOf(
                        SupportProbe(64, SupportMaterialClass.WATER),
                        SupportProbe(63, SupportMaterialClass.AIR),
                        SupportProbe(62, SupportMaterialClass.SOLID_SUPPORT)
                    )
                )
            }
        )

        val connections = RoutePlanner.planConnections(structures, rings, terrain, config)
        val bridge = connections.single().segments.single { it is PathSegment.Bridge }

        assertIs<PathSegment.Bridge>(bridge)
        assertEquals(5, bridge.blocks.size)
        assertTrue(bridge.supports.all { it.reachedSolid })
    }

    @Test
    fun route_detours_when_span_is_too_wide() {
        val structures = wideGapTwinStructures()
        val rings = structures.associate { it.id to RingPlanner.plan(it) }
        val terrain = RouteTerrainProfile(
            isGrassyBiome = true,
            defaultSurfaceY = 64,
            allowDetour = true,
            detourOffsetZ = 5,
            columns = (-7..7).associate { x ->
                (x to 0) to RouteTerrainProfile.waterColumn(
                    surfaceY = 64,
                    probes = listOf(
                        SupportProbe(64, SupportMaterialClass.WATER),
                        SupportProbe(63, SupportMaterialClass.AIR),
                        SupportProbe(62, SupportMaterialClass.SOLID_SUPPORT)
                    )
                )
            }
        )

        val connections = RoutePlanner.planConnections(structures, rings, terrain, config)
        val ground = connections.single().segments.single()

        assertIs<PathSegment.Ground>(ground)
        assertTrue(ground.blocks.any { it.z == 5 })
    }

    @Test
    fun route_skips_connection_when_wide_span_has_no_detour() {
        val structures = wideGapTwinStructures()
        val rings = structures.associate { it.id to RingPlanner.plan(it) }
        val terrain = RouteTerrainProfile(
            isGrassyBiome = true,
            defaultSurfaceY = 64,
            allowDetour = false,
            columns = (-7..7).associate { x ->
                (x to 0) to RouteTerrainProfile.waterColumn(
                    surfaceY = 64,
                    probes = listOf(
                        SupportProbe(64, SupportMaterialClass.WATER),
                        SupportProbe(63, SupportMaterialClass.AIR),
                        SupportProbe(62, SupportMaterialClass.SOLID_SUPPORT)
                    )
                )
            }
        )

        val connections = RoutePlanner.planConnections(structures, rings, terrain, config)

        assertTrue(connections.isEmpty())
    }

    private fun twinStructures(): List<StructureNode> =
        listOf(
            StructureNode(
                id = "alpha",
                structureKey = "test/alpha",
                center = BlockPos(-12, 64, 0),
                bounds = BoundingBox(-15, 64, -3, -9, 68, 3),
                ringPadding = 3,
                clusterRadius = 32
            ),
            StructureNode(
                id = "beta",
                structureKey = "test/beta",
                center = BlockPos(12, 64, 0),
                bounds = BoundingBox(9, 64, -3, 15, 68, 3),
                ringPadding = 3,
                clusterRadius = 32
            )
        )

    private fun wideGapTwinStructures(): List<StructureNode> =
        listOf(
            StructureNode(
                id = "alpha",
                structureKey = "test/alpha",
                center = BlockPos(-16, 64, 0),
                bounds = BoundingBox(-19, 64, -3, -13, 68, 3),
                ringPadding = 3,
                clusterRadius = 40
            ),
            StructureNode(
                id = "beta",
                structureKey = "test/beta",
                center = BlockPos(16, 64, 0),
                bounds = BoundingBox(13, 64, -3, 19, 68, 3),
                ringPadding = 3,
                clusterRadius = 40
            )
        )
}
