package com.example.data.supabase

import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Supabase Auth/PostgREST REST foundation compatible with minSdk 24.
 * Call methods from Dispatchers.IO; never from the Android main thread.
 * Only use the publishable key here. Never put service_role/secret keys in an Android app.
 */
class SupabaseRestClient(
    private val projectUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        projectUrl.startsWith("https://") && projectUrl.endsWith(".supabase.co") &&
            publishableKey.startsWith("sb_publishable_")

    /** Requests an SMS OTP. Phone Auth and an SMS provider must be enabled in Supabase first. */
    fun sendPhoneOtp(phoneE164: String): Result<String> {
        require(phoneE164.startsWith("+")) { "Use international phone format, e.g. +249..." }
        return request(
            path = "/auth/v1/otp",
            method = "POST",
            body = JSONObject().put("phone", phoneE164).put("create_user", true),
        )
    }

    /** Verifies the SMS code. The returned JSON includes the session when successful. */
    fun verifyPhoneOtp(phoneE164: String, smsCode: String): Result<String> {
        require(phoneE164.startsWith("+")) { "Use international phone format, e.g. +249..." }
        require(smsCode.isNotBlank()) { "SMS code is required" }
        return request(
            path = "/auth/v1/verify",
            method = "POST",
            body = JSONObject()
                .put("phone", phoneE164)
                .put("token", smsCode)
                .put("type", "sms"),
        )
    }

    /** Generic PostgREST request helper. Pass a user's access token for authenticated requests. */
    fun rest(
        tableOrRpcPath: String,
        method: String = "GET",
        body: String? = null,
        accessToken: String? = null,
        prefer: String? = null,
    ): Result<String> {
        val normalizedPath = tableOrRpcPath.trimStart('/')
        val endpointPath = normalizedPath.substringBefore('?')
        require(normalizedPath.startsWith("rest/v1/")) {
            "Use a PostgREST path under rest/v1/"
        }
        require(
            !endpointPath.contains('\\') &&
                endpointPath.split('/').none { it == "." || it == ".." } &&
                !Regex("(?i)%2e|%2f|%5c").containsMatchIn(endpointPath)
        ) {
            "PostgREST path must not contain path traversal segments"
        }
        val parsedBody = body?.let { JSONObject(it) }
        return request(normalizedPath, method, parsedBody, accessToken, prefer)
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        accessToken: String? = null,
        prefer: String? = null,
    ): Result<String> = runCatching {
        check(isConfigured()) {
            "Supabase is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties."
        }
        val builder = Request.Builder()
            .url(projectUrl.trimEnd('/') + "/" + path.trimStart('/'))
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer " + (accessToken?.takeIf { it.isNotBlank() } ?: publishableKey))
            .header("Accept", "application/json")
        prefer?.takeIf { it.isNotBlank() }?.let { builder.header("Prefer", it) }
        val requestBody = body?.toString()?.toRequestBody(jsonType)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(jsonType))
            "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(requestBody)
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }
        http.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    JSONObject(responseText).optString("message").ifBlank {
                        JSONObject(responseText).optString("msg").ifBlank { responseText }
                    }
                } catch (_: Exception) { responseText }
                throw IOException("Supabase HTTP ${response.code}: ${message.ifBlank { response.message }}")
            }
            responseText
        }
    }
}
