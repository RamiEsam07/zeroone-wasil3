package com.example.data.supabase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory session handoff for authenticated PostgREST calls in the customer app.
 * Tokens remain persisted only in SecureSessionStore's encrypted payload.
 */
object SupabaseSessionProvider {
    @Volatile
    var session: SupabaseSession? = null

    @Volatile
    var sessionRefresher: (suspend (SupabaseSession) -> SupabaseSession)? = null

    // Refresh tokens can rotate on use. Serialize refreshes so parallel API requests
    // cannot submit the same refresh token twice and accidentally invalidate the session.
    private val refreshMutex = Mutex()

    suspend fun validSessionOrNull(): SupabaseSession? {
        val current = session ?: return null
        if (!current.expiresSoon()) return current
        return refreshMutex.withLock {
            val latest = session ?: return@withLock null
            if (!latest.expiresSoon()) return@withLock latest
            val refresher = sessionRefresher ?: return@withLock latest
            try {
                refresher(latest).also { session = it }
            } catch (error: Exception) {
                if (error.message.orEmpty().contains("HTTP 400") ||
                    error.message.orEmpty().contains("HTTP 401")
                ) {
                    session = null
                }
                throw error
            }
        }
    }
}
