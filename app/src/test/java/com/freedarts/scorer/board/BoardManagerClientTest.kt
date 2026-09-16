package com.freedarts.scorer.board

import com.freedarts.scorer.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardManagerClientTest {
    private val client = BoardManagerClient(CoroutineScope(Dispatchers.Unconfined))

    @Test fun coordsOnlyWithUnitMm() {
        client.parse("""{"status":"Throw","numThrows":2,"throws":[
            {"segment":{"name":"T20"},"coords":{"x":1.5,"y":-100.25,"unit":"mm"}},
            {"segment":{"number":16,"multiplier":2},"coords":{"x":0.12,"y":0.3}}]}""")
        val t = client.state.value.throws
        assertEquals(Segment.triple(20), t[0].segment); assertEquals(1.5f, t[0].x); assertEquals(-100.25f, t[0].y)
        assertEquals(Segment(16, 2), t[1].segment); assertNull(t[1].x)   // Autodarts-Koordinaten ohne unit werden ignoriert
    }

    @Test fun tipsEmittedOncePerSeq() {
        val got = ArrayList<BoardManagerClient.TipBatch>()
        val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Unconfined) { client.tips.collect { got += it } }
        val body = """{"status":"Throw","numThrows":0,"throws":[],"tipSeq":7,"tips":[{"x":1.5,"y":-100.25,"conf":0.8},{"x":0,"y":0}]}"""
        client.parse(body); client.parse(body)                       // gleicher Stand zweimal gepollt
        client.parse(body.replace("\"tipSeq\":7", "\"tipSeq\":8"))   // neue Auswertung
        client.parse("""{"status":"Throw","numThrows":0,"throws":[]}""")   // Autodarts ohne tips
        job.cancel()
        assertEquals(listOf(7, 8), got.map { it.seq })
        assertEquals(BoardManagerClient.Tip(1.5f, -100.25f, 0.8f), got[0].tips[0])
        assertEquals(1f, got[0].tips[1].conf)
    }
}
