package com.freedarts.scorer.board

import com.freedarts.scorer.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
}
