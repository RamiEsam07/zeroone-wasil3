## Authentication decision: Google first, phone later

- First launch sign-in method: Google via native Android Credential Manager, exchanging the Google ID token for a Supabase Auth session.
- Phone/SMS OTP is deferred. Do not present it as an available sign-in option in the current release.
- Each Android application needs its own Android OAuth client in Google Cloud using that app's exact applicationId and SHA-1 signing fingerprint (debug and release fingerprints differ). Create a Web OAuth client too; configure the Web client ID and secret in Supabase Auth > Providers > Google. The Web client ID is used by the native Credential Manager request.
- Add `GOOGLE_WEB_CLIENT_ID` to local `local.properties` only. The customer app now uses Credential Manager to obtain a Google ID token, exchanges it with Supabase Auth, encrypts the session with Android Keystore, refreshes near-expiry sessions, and exposes sign-out. Do not commit OAuth client secrets. The Supabase publishable key is safe for client apps; service-role/secret keys are never safe in Android.
- Add the Google provider's redirect/configuration and verify the ID-token exchange, session persistence/refresh, sign-out, and role-based access before calling Google login complete.
- New Google accounts receive the default customer role. Merchant, driver, and admin access must be granted only through a trusted approval/provisioning process, never from client-provided metadata.
- Required provider setup: https://supabase.com/docs/guides/auth/social-login/auth-google?platform=android

# WASIL Supabase setup (Android)

1. Open the project in Android Studio.
2. In the project root, open your local `local.properties` file (do not commit it).
3. Add these two properties, using the project's URL and publishable key from Supabase:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_REPLACE_WITH_YOUR_KEY
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_OAUTH_CLIENT_ID.apps.googleusercontent.com
```

4. Sync Gradle. The app reads these values into BuildConfig at build time.
5. Configure Google as an Auth provider in Supabase and use the matching Web OAuth client ID in `local.properties`. Verify Android package name and SHA-1 fingerprints in Google Cloud. Phone/SMS OTP is deferred and is not presented in the current login screen.

Security:
- Never put a `service_role` key or a `secret` key in an Android app.
- The publishable key is not an authorization boundary. Enable RLS and create restrictive policies on every exposed table.
- Keep calls on an IO coroutine/dispatcher. Do not call synchronous OkHttp requests from the main thread.
- The customer app now selects `SupabaseDeliveryApiService` after authentication for live catalog/products, customer addresses, order creation/history, and cancellation. Order creation, cancellation, default-address changes and role/store approval use server-side RPCs. This wiring is code-complete in the review branch but is not runtime-verified until the migrations and Google provider are configured. Merchant, driver and admin apps are not implemented by this customer-app repository and still need their own role-specific integration.


## Database migration drafts

The following SQL files are review drafts, not applied to the Supabase project:

- `supabase/migrations/202610170001_initial_wasil_schema.sql` — initial tables and RLS policies.
- `supabase/migrations/202610170002_secure_order_rpcs.sql` — transactional order creation, first-driver-wins acceptance, and merchant status transitions.
- `supabase/migrations/202610170003_driver_lifecycle_and_cancellation.sql` — driver pickup/on-the-way/delivered transitions and cancellation with stock restoration.
- `supabase/migrations/202610170004_least_privilege_grants.sql` — column-level grants protect account activation, product activation, driver availability and commission balances; new stores and products start inactive and require admin approval.
- `supabase/migrations/202610170005_driver_single_active_order.sql` — serializes driver availability/acceptance and blocks drivers from taking multiple active deliveries.
- `supabase/migrations/202610170006_admin_provisioning.sql` — trusted role/activation provisioning and store approval RPCs.
- `supabase/migrations/202610170007_address_default_rpc.sql` — atomically sets a customer's default address and restricts editable address columns.

Use `WASIL_TONIGHT_RUNBOOK.md` for the execution sequence, role-based test matrix, Android integration order, and launch blockers. The `Supabase migration smoke check` GitHub Actions workflow applies all seven migrations to a disposable PostgreSQL database with a minimal Auth schema and runs basic RLS/grant checks; this is useful syntax/security smoke coverage, not a substitute for testing on Supabase itself. Do not run these in production yet. Validate the migrations in order against a disposable PostgreSQL/Supabase project, including concurrent acceptance and cancellation tests, and review the role-assignment workflow. The current order RPC intentionally supports cash on delivery only. Delivery fee, distance fee, tax, service fee and commission settings are stored server-side; their initial defaults are zero, and the store's configured delivery fee is honored. Do not enable non-zero production prices until the business approves the rates and verifies the checkout receipt against server-calculated totals. The Android customer app's debug build and the disposable PostgreSQL migration smoke workflow have passed on the review branch. These checks do not prove a live Supabase/Google sign-in or end-to-end order flow. Merchant, driver and admin role-specific app repositories are separate and must each be built and tested before launch. Order idempotency is implemented in the draft RPC/client contract but still needs retry/concurrency tests. Payment-provider integration remains deferred.
