@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.freedarts.scorer.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OnlineException(val status: Int, message: String) : Exception(message)

/**
 * Minimaler Supabase-Client ohne SDK: Auth (GoTrue), REST (PostgREST) und die URL für Realtime.
 * Funktioniert identisch mit supabase.com und mit dem selbst gehosteten Stack aus `selfhost/` – die App braucht
 * nur die Projekt-URL und den Anon-Key (bzw. Publishable Key).
 */
class SupabaseApi(baseUrl: String, val anonKey: String) {

    val baseUrl: String = baseUrl.trim().trimEnd('/')

    /** Access-Token der angemeldeten Sitzung; ohne Token laufen REST-Aufrufe als anon. */
    @Volatile var accessToken: String? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val realtimeUrl: String
        get() = baseUrl.replaceFirst("http", "ws") + "/realtime/v1/websocket?apikey=" + enc(anonKey) + "&vsn=1.0.0"

    // ---------- Auth ----------

    @Serializable
    private data class TokenResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long = 3600,
        val expires_at: Long? = null,
        val user: SessionUser? = null,
        /** Signup ohne Autoconfirm liefert nur den User (ohne Token). */
        val id: String? = null,
    )

    private fun session(text: String): OnlineSession {
        val t = json.decodeFromString(TokenResponse.serializer(), text)
        val token = t.access_token ?: throw OnlineException(200, "Konto angelegt – bitte zuerst die E-Mail bestätigen und dann anmelden.")
        return OnlineSession(
            accessToken = token,
            refreshToken = t.refresh_token ?: "",
            expiresAt = t.expires_at ?: (System.currentTimeMillis() / 1000 + t.expires_in),
            user = t.user ?: SessionUser(),
        )
    }

    suspend fun signUp(email: String, password: String, name: String): OnlineSession {
        val body = buildJsonObject {
            put("email", email.trim()); put("password", password)
            put("data", buildJsonObject { put("name", name.trim()) })
        }
        return session(call("POST", "/auth/v1/signup", body.toString(), auth = false))
    }

    suspend fun signInWithPassword(email: String, password: String): OnlineSession {
        val body = buildJsonObject { put("email", email.trim()); put("password", password) }
        return session(call("POST", "/auth/v1/token?grant_type=password", body.toString(), auth = false))
    }

    /** Gastkonto (bei Supabase "Anonymous sign-ins" bzw. ENABLE_ANONYMOUS_USERS). */
    suspend fun signInAnonymously(name: String): OnlineSession {
        val body = buildJsonObject { put("data", buildJsonObject { put("name", name.trim().ifEmpty { "Gast" }) }) }
        return session(call("POST", "/auth/v1/signup", body.toString(), auth = false))
    }

    suspend fun refresh(refreshToken: String): OnlineSession {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        return session(call("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), auth = false))
    }

    /** PKCE: Autorisierungscode aus dem Browser-Redirect gegen eine Sitzung tauschen. */
    suspend fun exchangeCode(code: String, verifier: String): OnlineSession {
        val body = buildJsonObject { put("auth_code", code); put("code_verifier", verifier) }
        return session(call("POST", "/auth/v1/token?grant_type=pkce", body.toString(), auth = false))
    }

    suspend fun signOut() {
        runCatching { call("POST", "/auth/v1/logout", "{}") }
    }

    /** Angemeldeter Benutzer (GET /auth/v1/user). */
    suspend fun user(): SessionUser = json.decodeFromString(SessionUser.serializer(), call("GET", "/auth/v1/user"))

    suspend fun updateUserName(name: String) {
        val body = buildJsonObject { put("data", buildJsonObject { put("name", name.trim()) }) }
        call("PUT", "/auth/v1/user", body.toString())
    }

    /** Browser-URL für Supabase OAuth (Google, GitHub, Discord …) mit PKCE; der Redirect führt zurück in die App. */
    fun authorizeUrl(provider: String, redirectTo: String, codeChallenge: String): String =
        "$baseUrl/auth/v1/authorize?provider=${enc(provider)}&redirect_to=${enc(redirectTo)}" +
            "&code_challenge=${enc(codeChallenge)}&code_challenge_method=s256"

    // ---------- REST (PostgREST) ----------

    suspend fun select(table: String, query: String): String = call("GET", "/rest/v1/$table?$query")

    suspend fun insert(table: String, body: String, returning: Boolean = true): String =
        call("POST", "/rest/v1/$table", body, mapOf("Prefer" to if (returning) "return=representation" else "return=minimal"))

    /** Insert mit Upsert-Semantik (Primärschlüssel-Konflikt → Zeile ersetzen). */
    suspend fun upsert(table: String, body: String): String =
        call("POST", "/rest/v1/$table", body, mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))

    suspend fun update(table: String, query: String, body: String): String =
        call("PATCH", "/rest/v1/$table?$query", body, mapOf("Prefer" to "return=representation"))

    suspend fun delete(table: String, query: String): String = call("DELETE", "/rest/v1/$table?$query")

    suspend fun rpc(function: String, args: JsonObject): String = call("POST", "/rest/v1/rpc/$function", args.toString())

    // ---------- HTTP ----------

    private suspend fun call(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap(), auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(baseUrl + path)
                .header("apikey", anonKey)
                .header("Accept", "application/json")
            val token = if (auth) accessToken else null
            if (token != null) b.header("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> b.header(k, v) }
            val rb = body?.toRequestBody(JSON_TYPE)
            when (method) {
                "GET" -> b.get()
                "DELETE" -> if (rb != null) b.delete(rb) else b.delete()
                else -> b.method(method, rb ?: "".toRequestBody(JSON_TYPE))
            }
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) throw OnlineException(r.code, errorMessage(text, r.code))
                text
            }
        }

    private fun errorMessage(text: String, code: Int): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val msg = listOf("msg", "message", "error_description", "error", "hint")
            .firstNotNullOfOrNull { k -> obj?.get(k)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
        return when {
            msg != null -> msg
            code == 401 -> "Nicht angemeldet"
            code == 404 -> "Nicht gefunden (Schema eingespielt?)"
            else -> "HTTP $code"
        }
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; coerceInputValues = true; isLenient = true }
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
