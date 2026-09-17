package com.freedarts.scorer.remote

import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kopplungsschlüssel des Remote-Servers: Steuern, Kamerabild und Kopplung gehen nur mit dem Schlüssel aus dem
 * QR-Code. Ohne ihn konnte jeder im WLAN das Handy mitten im Spiel in die Zweitgeräte-Ansicht zwingen.
 */
class RemoteAuthTest {

    private val commands = mutableListOf<String>()
    private val server = RemoteServer(
        stateProvider = { RemoteServer.RemoteState(hasGame = false) },
        frameProvider = { ByteArray(3) },
        onCommand = { commands.add(it) },
    )

    @After fun tearDown() = server.stop()

    private fun get(path: String): Int {
        val c = URL("http://127.0.0.1:${RemoteServer.PORT}$path").openConnection() as HttpURLConnection
        c.setRequestProperty("X-Scorelens", "1") // eigene Seite, wie sie der Browser sendet
        c.connectTimeout = 2000; c.readTimeout = 2000
        return try { c.responseCode } finally { c.disconnect() }
    }

    @Test fun keyGuardsControlCameraAndPairing() {
        org.junit.Assume.assumeTrue("Port ${RemoteServer.PORT} belegt", server.start())
        val key = server.token

        get("/cmd?do=next")
        assertTrue("ohne Schlüssel darf nichts gesteuert werden: $commands", commands.isEmpty())
        get("/pair?url=http%3A%2F%2F127.0.0.1%3A9999")
        assertTrue("ohne Schlüssel darf nichts koppeln: $commands", commands.isEmpty())
        assertEquals(403, get("/board.jpg"))

        get("/cmd?do=next&k=$key")
        assertEquals(listOf("next"), commands.toList())
        get("/pair?url=http%3A%2F%2F127.0.0.1%3A9999&k=$key")
        assertEquals(listOf("next", "pair:http://127.0.0.1:9999"), commands.toList())
        assertEquals(200, get("/board.jpg?k=$key"))

        // Anzeige bleibt ohne Schlüssel lesbar (Tablet, TV, OBS-Overlay)
        assertEquals(200, get("/state"))
        assertEquals(200, get("/overlay"))
    }
}
