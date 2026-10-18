package com.example.data.supabase

/**
 * In-memory session handoff for authenticated PostgREST calls in the customer app.
 * Tokens remain persisted only in SecureSessionStore's encrypted payload.
 */
object SupabaseSessionProvider {
    @Volatile
    var session: SupabaseSession? = null

    @Volatile
    var sessionRefresher: (suspend (SupabaseSession) -> SupabaseSession)? = null

    suspend fun validSessionOrNull(): SupabaseSession? {
        val current = session ?: return null
        if (!current.expiresSoon()) return current
        val refresher = sessionRefresher ?: return current
        return try {
            refresher(current).also { session = it }
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
