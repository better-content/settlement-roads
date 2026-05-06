package com.gerald.settlementroads.planner.placement

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentIdCodecTest {
    @Test
    fun ring_ids_are_stable_for_tracking() {
        assertEquals("castle:ring", SegmentIdCodec.ring("castle"))
    }

    @Test
    fun connection_ids_are_stable_for_tracking() {
        assertEquals("route:5", SegmentIdCodec.connection("route", 5))
    }
}
