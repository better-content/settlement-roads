package com.bettercontent.settlementroads.planner.placement

object SegmentIdCodec {
    fun ring(structureId: String): String =
        "$structureId:ring"

    fun connection(connectionId: String, index: Int): String =
        "$connectionId:$index"
}
