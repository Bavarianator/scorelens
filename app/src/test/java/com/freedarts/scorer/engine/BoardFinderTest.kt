package com.freedarts.scorer.engine

import com.freedarts.scorer.lens.BoardFinder
import com.freedarts.scorer.lens.Homography
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BoardFinderTest {
    private val w = 360; private val h = 480

    /** Rendert ein Board mit Standardfarben über eine Homographie Board→Bild. */
    private fun render(hb2i: Homography, noise: Int = 0): IntArray {
        val inv = hb2i.inverse()!!
        val img = IntArray(w * h)
        val rnd = java.util.Random(7)
        for (y in 0 until h) for (x in 0 until w) {
            val (bx, by) = inv.map(x + 0.5, y + 0.5)
            val r = hypot(bx, by)
            val seg = Board.segmentAt(bx, by)
            var color = when {
                r > 228 -> 0x5A5A60
                r > Board.DOUBLE_OUTER -> 0x141414
                seg.isBull -> if (seg.multiplier == 2) 0xC62828 else 0x2E7D32
                else -> {
                    val idx = Board.NUMBERS.indexOf(seg.number)
                    val dark = idx % 2 == 0
                    if (seg.multiplier >= 2) (if (dark) 0xC62828 else 0x2E7D32) else (if (dark) 0x151515 else 0xE8DCC0)
                }
            }
            if (noise > 0) {
                val n = rnd.nextInt(2 * noise + 1) - noise
                val rr = ((color shr 16 and 0xFF) + n).coerceIn(0, 255); val gg = ((color shr 8 and 0xFF) + n).coerceIn(0, 255); val bb = ((color and 0xFF) + n).coerceIn(0, 255)
                color = (rr shl 16) or (gg shl 8) or bb
            }
            img[y * w + x] = color
        }
        return img
    }

    private fun truth(s: Double, cx: Double, cy: Double, alphaDeg: Double, g: Double, hh: Double): Homography {
        val a = Math.toRadians(alphaDeg)
        return Homography(doubleArrayOf(s * cos(a), s * sin(a), cx, s * sin(a), -s * cos(a), cy, g, hh, 1.0))
    }

    private fun check(hTrue: Homography, noise: Int, maxErrPx: Double) {
        val img = render(hTrue, noise)
        val finder = BoardFinder(w, h).apply { log = { println("BF: $it") } }
        val fit = finder.find(img)
        assertNotNull("Board nicht gefunden", fit)
        fit!!
        assertEquals(BoardFinder.Quality.GOOD, fit.quality)
        var worst = 0.0
        for (r in doubleArrayOf(30.0, 103.0, 166.0)) {
            var wr = 0.0; var wdeg = 0
            for (deg in 0 until 360 step 15) {
                val ang = Math.toRadians(deg.toDouble())
                val (tx, ty) = hTrue.map(r * sin(ang), r * cos(ang))
                val (fx, fy) = fit.boardToImage.map(r * sin(ang), r * cos(ang))
                val e = hypot(tx - fx, ty - fy)
                if (e > wr) { wr = e; wdeg = deg }
            }
            println("BF-ERR r=$r worst=$wr px at $wdeg°")
            worst = maxOf(worst, wr)
        }
        assertTrue("max. Abweichung $worst px (Residuum ${fit.residualMm} mm)", worst < maxErrPx)
        // Segmentzuordnung an Testpunkten
        for (seg in listOf(Segment.triple(20), Segment.double(16), Segment.single(6), Segment.BULL, Segment.double(3), Segment.triple(11))) {
            val (bx, by) = Board.centerOf(seg)
            val (ix, iy) = hTrue.map(bx, by)
            val (rx, ry) = fit.imageToBoard.map(ix, iy)
            assertEquals(seg, Board.segmentAt(rx, ry))
        }
    }

    @Test fun frontalView() = check(truth(0.8, 180.0, 240.0, 0.0, 0.0, 0.0), 0, 2.0)

    @Test fun sideViewWithPerspectiveAndTilt() = check(truth(0.78, 178.0, 250.0, 6.0, 0.0006, 0.0004), 12, 3.0)

    @Test fun smallRotatedBoard() = check(truth(0.55, 200.0, 220.0, -12.0, -0.0004, 0.0005), 8, 2.5)

    @Test fun ellipseFitDiagnostics() {
        val hTrue = truth(0.78, 178.0, 250.0, 6.0, 0.0006, 0.0004)
        val pts = (0 until 180).map { k -> val a = Math.toRadians(k * 2.0); hTrue.map(170 * sin(a), 170 * cos(a)) }
        val xs = pts.map { it.first }; val ys = pts.map { it.second }
        println("BF-DIAG extents x=${xs.min()}..${xs.max()} y=${ys.min()}..${ys.max()}")
        val e = BoardFinder(w, h).fitEllipse(pts)
        println("BF-DIAG fit=$e")
        val e2 = BoardFinder(w, h).fitEllipse(pts.filterIndexed { i, _ -> i % 3 == 0 })
        println("BF-DIAG fit(sparse)=$e2")
        assertNotNull(e)
    }

    /** Verfeinerung ab einer gestörten Start-Homographie (wie aus KI-Punkten) muss zur Wahrheit konvergieren. */
    @Test fun refineFromPerturbedStart() {
        val hTrue = truth(0.78, 178.0, 250.0, 6.0, 0.0006, 0.0004)
        val img = render(hTrue, 10)
        // Startlösung: 6 KI-artige Punkte mit ±3 px Rauschen
        val rnd = java.util.Random(3)
        val classes = listOf(-9.0, 171.0, 261.0, 81.0, 297.0, 117.0)
        val bp = classes.map { Math.toRadians(it) }.map { 170 * sin(it) to 170 * cos(it) }
        val ip = bp.map { (bx, by) -> val (x, y) = hTrue.map(bx, by); x + rnd.nextGaussian() * 3 to y + rnd.nextGaussian() * 3 }
        val h0 = Homography.from(bp, ip)!!
        val fit = BoardFinder(w, h).refine(img, h0)
        assertNotNull(fit); fit!!
        assertEquals(BoardFinder.Quality.GOOD, fit.quality)
        var worst0 = 0.0; var worst = 0.0
        for (deg in 0 until 360 step 10) for (r in doubleArrayOf(50.0, 103.0, 166.0)) {
            val a = Math.toRadians(deg.toDouble())
            val (tx, ty) = hTrue.map(r * sin(a), r * cos(a))
            val (x0, y0) = h0.map(r * sin(a), r * cos(a))
            val (fx, fy) = fit.boardToImage.map(r * sin(a), r * cos(a))
            worst0 = maxOf(worst0, hypot(tx - x0, ty - y0)); worst = maxOf(worst, hypot(tx - fx, ty - fy))
        }
        println("BF-REFINE start worst=$worst0 px → refined worst=$worst px, residual=${fit.residualMm} mm")
        assertTrue("Verfeinerung hat nicht verbessert: $worst0 → $worst", worst < worst0 / 2 && worst < 2.5)
    }

    @Test fun rejectsEmptyImage() {
        val img = IntArray(w * h) { 0x303030 }
        assertEquals(null, BoardFinder(w, h).find(img))
    }
}
