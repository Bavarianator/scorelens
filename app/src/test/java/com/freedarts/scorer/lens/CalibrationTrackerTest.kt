package com.freedarts.scorer.lens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTrackerTest {

    @Test fun medianAndDrift() {
        val c = CalibrationTracker(360, 480)
        val pts = listOf(100.0 to 100.0, 260.0 to 100.0, 260.0 to 380.0, 100.0 to 380.0)
        assertNull(c.stable(pts)) // ein Fit reicht nicht
        val jitter = pts.map { it.first + 1.0 to it.second - 1.0 }
        val med = c.stable(jitter)!!
        assertEquals(100.5, med[0].first, 1e-9)
        assertEquals(99.5, med[0].second, 1e-9)

        val norm = c.normalized(med)
        assertEquals(8, norm.size)
        assertFalse(c.moved(norm, norm))
        val shifted = norm.mapIndexed { i, v -> if (i % 2 == 0) v + 10f / 360f else v } // 10 px in x
        assertFalse(c.moved(norm, shifted)) // erst einmal gesehen
        assertTrue(c.moved(norm, shifted)) // zweimal in Folge → Kamera bewegt

        assertNull(c.stable(pts.map { it.first + 50 to it.second })) // Ausreißer: nicht stabil
    }
}
