package com.freedarts.scorer.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendLinkTest {
    private val id = "0f8b6c1e-3a4d-4b5e-9c7f-1a2b3c4d5e6f"

    @Test fun `QR-Inhalt und geteilter Link liefern die Nutzer-ID`() {
        assertEquals(id, OnlineController.friendIdFrom("scorelens://friend/$id"))
        assertEquals(id, OnlineController.friendIdFrom(" https://xyz.supabase.co/functions/v1/friend/$id \n"))
    }

    @Test fun `Fremde Texte werden abgelehnt`() {
        assertNull(OnlineController.friendIdFrom("https://example.com/$id"))
        assertNull(OnlineController.friendIdFrom("scorelens://friend/kurz"))
        assertNull(OnlineController.friendIdFrom("scorelens://auth/callback?code=$id"))
    }
}
