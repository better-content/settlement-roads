package com.gerald.settlementroads.planner.placement

object SegmentIdCodec {
    private const val PLACEMENT_VERSION = 2

    fun ring(structureId: String): String =
        "$structureId:ring:v$PLACEMENT_VERSION"

    fun connection(connectionId: String, index: Int): String =
        "$connectionId:$index:v$PLACEMENT_VERSION"
}
