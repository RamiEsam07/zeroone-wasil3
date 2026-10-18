package com.example.data.supabase

/**
 * In-memory session handoff for authenticated PostgREST calls in the customer app.
 * Tokens remain persisted only in SecureSessionStore's encrypted payload.
 */
object SupabaseSessionProvider {
    @Volatile
    var session: SupabaseSession? = null
}
