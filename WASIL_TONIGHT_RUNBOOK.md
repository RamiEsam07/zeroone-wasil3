# WASIL execution and validation runbook

This document is a practical checklist, not a claim that tests have already passed.

## Current safety boundary

- The customer/database review branch is `ops/wasil-integration-audit`. Keep `main` unchanged until the complete pull request has been reviewed, SQL validation in a disposable environment is satisfactory, and Android build checks are green for the exact latest commit.
- The customer, merchant/store, driver, and admin applications are separate repositories/review branches. CI checks are not real-device or live-Supabase runtime verification.
- The eight SQL migrations in this repository are drafts and have **not** been applied to the Supabase project.
- Never put a Supabase `service_role` or secret key in Android code, committed `local.properties`, screenshots, or chat.
- Google login is wired in the customer app code, but requires `GOOGLE_WEB_CLIENT_ID`, the matching Android package/SHA-1 configuration, and Google provider setup in Supabase before runtime testing. Phone OTP is deferred.
- Supabase Auth session persistence uses Android Keystore encryption and refreshes near expiry; this still requires runtime verification on an emulator/device.
- The current order RPC is cash-on-delivery only. Initial server-side platform fee/tax/commission settings default to zero unless configured; the store delivery fee and minimum order amount are calculated on the server. Platform commission is recorded separately and does not increase the customer's payable total. Keep non-zero rates disabled until checkout estimates are validated against server-calculated totals.

## Role-app review links

These links are reference locations, not evidence that the pull requests are merged or runtime-tested:

- Customer app + database migrations: https://github.com/RamiEsam07/zeroone-wasil3/pull/3
- Merchant/store app: https://github.com/RamiEsam07/zeroone-wasil/pull/3
- Driver app: https://github.com/RamiEsam07/zeroone-wasil4/pull/3
- Admin app: https://github.com/RamiEsam07/zeoone---wasil/pull/3

Keep each role-specific application separate until the relevant code review and runtime checks are complete.

## Before touching the live Supabase project

1. Create/use a disposable Supabase project or local PostgreSQL environment.
2. Apply migrations in order, one at a time:
   - `202610170001_initial_wasil_schema.sql`
   - `202610170002_secure_order_rpcs.sql`
   - `202610170003_driver_lifecycle_and_cancellation.sql`
   - `202610170004_least_privilege_grants.sql`
   - `202610170005_driver_single_active_order.sql`
   - `202610170006_admin_provisioning.sql`
   - `202610170007_address_default_rpc.sql`
   - `202610170008_admin_update_store_atomic.sql`
3. Stop on the first SQL error; do not continue to later migrations.
4. Seed test users for customer, merchant, driver, and admin using a trusted administrative path. Do not make role assignment available to normal clients.
5. Confirm RLS and table/function privileges with each role before any real customer data is entered.
6. Bootstrap the first admin through a trusted Supabase SQL Editor workflow using the reviewed `supabase/bootstrap_first_admin.sql.example`; replace placeholders carefully and verify the account UUID and email before execution. Never run it from the Android app.
7. To onboard a merchant or driver, first create their Supabase Auth/profile account, then use the admin provisioning RPC to assign the approved role and activate the account. The driver profile is created by trusted provisioning; driver clients cannot create it themselves.
8. New stores and products remain inactive until admin approval/activation. Merchants may prepare store/product details, but those records must not appear in the customer catalog until the moderation flags are enabled by the admin RPC.

## Required database test matrix

The GitHub Actions workflow `Supabase migration smoke check` applies the eight migrations present in the branch to a disposable PostgreSQL database and runs the SQL smoke suite. Confirm the latest commit has green checks before considering merge. These checks do not connect to the live Supabase project and do not replace full Supabase, device-level Google Auth, or multi-client concurrency testing.

| Scenario | Expected result |
|---|---|
| Anonymous user reads active catalog | Allowed only for active/open catalog rows per policies |
| Customer reads another customer's address | Denied |
| Customer directly inserts/updates an order or order item | Denied |
| Customer creates an order with a valid address and in-stock items | Order + snapshots created atomically; stock decreases once |
| Same customer retries the same order with the same idempotency key | Existing order is returned; stock is not decremented again |
| Customer submits a product from another store, unavailable item, duplicate ID, invalid quantity, or insufficient stock | Entire transaction fails; no order/stock change |
| Two customers order the last unit concurrently | At most one succeeds |
| Two drivers accept the same ready order concurrently | Exactly one succeeds |
| One driver attempts to accept two orders concurrently | Second acceptance is rejected |
| Driver attempts to mark available or go offline with an active delivery | Rejected |
| Driver publishes coordinates | Only an active driver can call the location RPC; the server stamps `last_location_at`; direct client timestamp/coordinate writes are denied |
| Auto-assignment runs | Only an active available driver with a fresh location, no active order, and distance within the configured radius is eligible |
| Merchant changes a status outside placed → confirmed → preparing → ready_for_pickup | Rejected |
| Unassigned driver changes an order status | Rejected |
| Customer cancels own placed/confirmed order | Cancelled and stock restored once |
| Customer cancels another customer's order or cancels after pickup | Rejected |
| Merchant cancels own store's placed/confirmed/preparing order | Cancelled and stock restored once |
| Repeated cancellation call | No second stock restoration |
| Driver completes assigned delivery | Status reaches delivered; delivered_at is set; driver becomes available |
| Client updates profile role, activation, or driver commission balance | Denied |
| Client attempts RPC as wrong role | Denied |

Treat scenarios in this matrix as **required expected behavior**, not as individually proven unless the test run explicitly covers them.

## Android integration sequence

1. Add the project's Supabase URL, publishable key, and Google Web OAuth client ID to the customer app's local `local.properties` only; do not commit OAuth secrets.
2. Sync Gradle and build each of the four apps independently.
3. Fix compile/configuration errors before connecting screens.
4. Customer catalog, addresses, order creation/history/cancellation, merchant store/product onboarding and order-status RPCs, driver acceptance/status/location publishing, and admin provisioning/settings must each be validated end-to-end with disposable test accounts. Do not treat code presence as proof that a flow works.
5. Use a real user's access token for authenticated PostgREST/RPC calls. Never treat the publishable key as user authorization.
6. Confirm session persistence, sign-out, token expiry/refresh, loading/error states, and offline/network failure behavior.
7. Test on an emulator/device with test accounts and disposable data.

## Launch blockers to resolve before production

- Validate the first-admin bootstrap and the admin provisioning RPCs for customer/merchant/driver roles and store approval.
- Validate order idempotency under retry and concurrent duplicate requests.
- Confirm server-side delivery-fee rules, service area, and who receives each fee.
- Integrate and test a payment provider before enabling electronic payment.
- Push notifications and Supabase Realtime remain follow-up work; customer order/history screens currently poll order status every 15 seconds while visible.
- Define and test the driver commission/settlement ledger and limits if commissions are part of launch.
- Define privacy/retention rules for precise customer/driver location.
- Complete device-level Google OAuth/session tests and live Supabase end-to-end tests for all four apps.
- Verify store coordinates can be entered in merchant onboarding/profile and that driver GPS permission/location publishing works only while available or on an active delivery.

## Completion criteria

The customer foundation's CI phase can be described as provisionally passed only when the latest exact commit has a successful Android build and all eight migration drafts apply in the disposable PostgreSQL workflow with the smoke suite. Do not call production integration complete until configured Google/Supabase sign-in, a real test order, role-based merchant/driver/admin workflows, and concurrency tests succeed. GitHub files and CI alone do not satisfy those launch criteria.
