package com.example.data.supabase

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth REST client. Call from coroutines; network work runs on Dispatchers.IO.
 * Google OAuth must be enabled in Supabase and use the matching Web OAuth client ID.
 */
class SupabaseAuthRepository(
    private val sessionStore: SecureSessionStore,
    private val projectUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        projectUrl.startsWith("https://") &&
            projectUrl.endsWith(".supabase.co") &&
            publishableKey.startsWith("sb_publishable_")

    suspend fun signInWithGoogleIdToken(idToken: String): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConfigured()) { "أكمل إعداد رابط Supabase ومفتاح النشر أولاً." }
                require(idToken.isNotBlank()) { "Google ID token is missing." }
                val response = postAuth(
                    path = "/auth/v1/token?grant_type=id_token",
                    body = JSONObject()
                        .put("provider", "google")
                        .put("id_token", idToken),
                )
                parseAndSaveSession(response, fallback = null)
            }
        }

    /** Restores a saved session and refreshes it before expiry. */
    suspend fun restoreSession(): Result<SupabaseSession?> = withContext(Dispatchers.IO) {
        runCatching {
            val saved = sessionStore.load() ?: return@runCatching null
            ensureValidSession(saved).getOrThrow()
        }
    }

    /** Refreshes a session only when it is near expiry; transient failures do not erase it. */
    suspend fun ensureValidSession(session: SupabaseSession): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!session.expiresSoon()) return@runCatching session
                try {
                    val response = postAuth(
                        path = "/auth/v1/token?grant_type=refresh_token",
                        body = JSONObject().put("refresh_token", session.refreshToken),
                    )
                    parseAndSaveSession(response, fallback = session)
                } catch (error: Exception) {
                    if (error is AuthHttpException && error.statusCode in 400..401) {
                        sessionStore.clear()
                    }
                    throw error
                }
            }
        }

    /** Reads the server-owned profile role; client metadata is never trusted for authorization. */
    suspend fun getAccountAccess(session: SupabaseSession): Result<SupabaseAccountAccess> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = SupabaseRestClient(projectUrl, publishableKey).rest(
                    tableOrRpcPath = "rest/v1/profiles?select=role,is_active&id=eq." + session.userId,
                    accessToken = session.accessToken,
                ).getOrThrow()
                val rows = JSONArray(response)
                check(rows.length() > 0) { "لم يتم العثور على ملف الحساب في قاعدة البيانات." }
                val profile = rows.getJSONObject(0)
                SupabaseAccountAccess(
                    role = profile.optString("role"),
                    isActive = profile.optBoolean("is_active", false),
                )
            }
        }

    suspend fun signOut(session: SupabaseSession?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                if (session != null && isConfigured()) {
                    val request = Request.Builder()
                        .url(projectUrl.trimEnd('/') + "/auth/v1/logout")
                        .header("apikey", publishableKey)
                        .header("Authorization", "Bearer " + session.accessToken)
                        .post("{}".toRequestBody(jsonType))
                        .build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 401) {
                            throw IOException("Supabase logout failed with HTTP " + response.code)
                        }
                    }
                }
            } finally {
                sessionStore.clear()
            }
        }
    }

    private fun postAuth(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(projectUrl.trimEnd('/') + path)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer " + publishableKey)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    JSONObject(responseText).optString("msg").ifBlank {
                        JSONObject(responseText).optString("message").ifBlank { responseText }
                    }
                } catch (_: Exception) { responseText }
                throw AuthHttpException(
                    response.code,
                    "Supabase Auth HTTP " + response.code + ": " + (message.ifBlank { response.message }),
                )
            }
            return JSONObject(responseText)
        }
    }

    private class AuthHttpException(
        val statusCode: Int,
        message: String,
    ) : IOException(message)

    private fun parseAndSaveSession(
        response: JSONObject,
        fallback: SupabaseSession?,
    ): SupabaseSession {
        val user = response.optJSONObject("user")
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw IOException("Supabase Auth response did not include an access token.")
        val refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: fallback?.refreshToken
            ?: throw IOException("Supabase Auth response did not include a refresh token.")
        val userId = user?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
            ?: fallback?.userId
            ?: throw IOException("Supabase Auth response did not include a user ID.")
        val expiresAt = response.optLong("expires_at").takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + response.optLong("expires_in", 3600))

        val session = SupabaseSession(
            userId = userId,
            email = user?.optString("email")?.takeIf { it.isNotBlank() && it != "null" } ?: fallback?.email,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
        )
        sessionStore.save(session)
        return session
    }
}
