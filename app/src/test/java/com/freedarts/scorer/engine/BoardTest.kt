package com.freedarts.scorer.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BoardTest {

    @Test fun wireDistanceToRings() {
        assertEquals(4.0, Board.distanceToWire(0.0, 103.0), 1e-9)  // T20-Mitte: 4 mm zu beiden Triple-Kanten
        assertEquals(4.0, Board.distanceToWire(0.0, 166.0), 1e-9)  // D20-Mitte
        assertEquals(1.35, Board.distanceToWire(0.0, 5.0), 1e-9)   // im Bull, keine Sektordrähte
    }

    @Test fun wireDistanceToSectorWires() {
        val onWire = Math.toRadians(9.0) // Draht zwischen 20 und 1
        assertEquals(0.0, Board.distanceToWire(50 * sin(onWire), 50 * cos(onWire)), 1e-9)
        val oneDegOff = Math.toRadians(8.0)
        assertEquals(50 * Math.toRadians(1.0), Board.distanceToWire(50 * sin(oneDegOff), 50 * cos(oneDegOff)), 1e-6)
    }
}
