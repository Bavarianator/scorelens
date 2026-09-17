package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.SegmentTrainingGame
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Segment-Training mit mehreren Spielern und „Ende nach Treffern“: Wer fertig ist, verlässt die Reihenfolge –
 * sonst zählen weitere Darts gegen „wenigste Darts gewinnt“ und Bots werfen einfach weiter.
 */
class PracticeMultiPlayerTest {

    private val a = Player(id = "a", name = "Anna")
    private val b = Player(id = "b", name = "Ben")

    private fun game() = SegmentTrainingGame(
        listOf(a, b),
        GameSettings(mode = GameMode.SEGMENT_TRAINING, trainingSegment = 20, hitMode = HitMode.TRIPLE, endAfterHits = true, hitCount = 1),
        seed = 1,
    )

    @Test fun finishedPlayerLeavesRotation() {
        val g = game()
        g.throwDart(Segment.triple(20)) // Anna ist mit einem Dart fertig
        assertTrue(g.snapshot().players[0].isOut)
        assertFalse(g.finished)
        assertEquals(1, g.snapshot().currentPlayer)

        // Bens Aufnahme geht zu Ende, danach ist wieder Ben dran (nicht Anna)
        repeat(3) { g.throwDart(Segment.MISS) }
        assertEquals(1, g.snapshot().currentPlayer)

        g.throwDart(Segment.triple(20))
        assertTrue(g.finished)
        assertEquals(0, g.winner) // Anna: 1 Dart, Ben: 4 Darts
    }
}
