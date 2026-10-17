# WASIL Supabase setup (Android)

1. Open the project in Android Studio.
2. In the project root, open your local `local.properties` file (do not commit it).
3. Add these two properties, using the project's URL and publishable key from Supabase:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_REPLACE_WITH_YOUR_KEY
```

4. Sync Gradle. The app reads these values into BuildConfig at build time.
5. In Supabase, enable Phone Auth and configure an SMS provider before testing OTP.

Security:
- Never put a `service_role` key or a `secret` key in an Android app.
- The publishable key is not an authorization boundary. Enable RLS and create restrictive policies on every exposed table.
- Keep calls on an IO coroutine/dispatcher. Do not call synchronous OkHttp requests from the main thread.
- This PR adds a low-level REST/auth foundation only. It does not yet replace existing mock/Firebase repositories or connect screens; those need role-specific migrations and tests.


## Database migration drafts

The following SQL files are review drafts, not applied to the Supabase project:

- `supabase/migrations/202610170001_initial_wasil_schema.sql` — initial tables and RLS policies.
- `supabase/migrations/202610170002_secure_order_rpcs.sql` — transactional order creation, first-driver-wins acceptance, and merchant status transitions.

Do not run these in production yet. First validate both migrations against a disposable PostgreSQL/Supabase project and review the role-assignment workflow. The current order RPC intentionally supports cash on delivery only and uses a zero delivery fee until a server-side fee configuration is implemented. Driver pickup/delivery status transitions, order idempotency, and payment-provider integration remain follow-up work.
