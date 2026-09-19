package com.bettercontent.settlementroads.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementRoadsRuntimeTimingTest {
    @Test
    fun firstAttemptAndClockResetAreDueWithoutOverflow() {
        assertTrue(SettlementRoadsRuntime.elapsed(0, null, 5))
        assertTrue(SettlementRoadsRuntime.elapsed(42, null, 5))
        assertFalse(SettlementRoadsRuntime.elapsed(103, 100, 5))
        assertTrue(SettlementRoadsRuntime.elapsed(105, 100, 5))
        assertTrue(SettlementRoadsRuntime.elapsed(1, 100, 5))
        assertTrue(SettlementRoadsRuntime.elapsed(Long.MAX_VALUE, Long.MAX_VALUE - 5, 5))
    }
}
