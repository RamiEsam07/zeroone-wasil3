# WASIL tonight execution and validation runbook

This document is a practical checklist, not a claim that tests have already passed.

## Current safety boundary

- Review and corrective commits are isolated on `fix/wasil-foundation-review`; keep `main` unchanged. Do not merge this branch or the foundation PR until code review, SQL validation in a disposable environment, and Android build checks are complete.
- The four app PRs are a low-level Supabase foundation; their existing screens and repositories are not yet proven to use Supabase.
- SQL migrations are drafts and have **not** been applied to the Supabase project.
- Never put a Supabase `service_role` or secret key in Android code, `local.properties` committed to Git, screenshots, or chat.
- Google login is wired in the customer app code, but requires `GOOGLE_WEB_CLIENT_ID`, the matching Android package/SHA-1 configuration, and Google provider setup in Supabase before runtime testing. Phone OTP is deferred.
- Supabase Auth session persistence uses Android Keystore encryption and refreshes near expiry; this still requires runtime verification on an emulator/device.
- The current order RPC is cash-on-delivery only and has a zero delivery fee. Do not treat this as launch-ready pricing.

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
3. Stop on the first SQL error; do not continue to later migrations.
4. Seed test users for customer, merchant, driver, and admin using a trusted administrative path. Do not make role assignment available to normal clients.
5. Confirm RLS and table/function privileges with each role before any real customer data is entered.

## Required database test matrix

| Scenario | Expected result |
|---|---|
| Anonymous user reads active catalog | Allowed only for active/open catalog rows per policies |
| Customer reads another customer's address | Denied |
| Customer directly inserts/updates an order or order item | Denied |
| Customer creates an order with a valid address and in-stock items | Order + snapshots created atomically; stock decreases once |
| Customer submits a product from another store, unavailable item, duplicate ID, invalid quantity, or insufficient stock | Entire transaction fails; no order/stock change |
| Two customers order the last unit concurrently | At most one succeeds |
| Two drivers accept the same ready order concurrently | Exactly one succeeds |
| One driver attempts to accept two orders concurrently | Second acceptance is rejected |
| Driver attempts to mark available with an active delivery | Rejected |
| Merchant changes a status outside placed → confirmed → preparing → ready_for_pickup | Rejected |
| Unassigned driver changes an order status | Rejected |
| Customer cancels own placed/confirmed order | Cancelled and stock restored once |
| Customer cancels another customer's order or cancels after pickup | Rejected |
| Merchant cancels own store's placed/confirmed/preparing order | Cancelled and stock restored once |
| Repeated cancellation call | No second stock restoration |
| Driver completes assigned delivery | Status reaches delivered; delivered_at is set; driver becomes available |
| Client updates profile role, activation, or driver commission balance | Denied |
| Client attempts RPC as wrong role | Denied |

## Android integration sequence

1. Add the project's Supabase URL, publishable key, and Google Web OAuth client ID to the customer app's local `local.properties` only; do not commit OAuth secrets.
2. Sync Gradle and build each of the four apps independently.
3. Fix compile/configuration errors before connecting screens.
4. Auth/session code is now present for Google → Supabase; validate it first, then wire one shared contract at a time: catalog → addresses → create order → merchant status → driver accept/status → order history/admin views.
5. Use a real user's access token for authenticated PostgREST/RPC calls. Never treat the publishable key as user authorization.
6. Confirm session persistence, sign-out, token expiry/refresh, loading/error states, and offline/network failure behavior.
7. Test on an emulator/device with test accounts and disposable data.

## Launch blockers to resolve

- Bootstrap the first admin through a trusted Supabase SQL Editor/service-role workflow; then test the admin provisioning RPCs for merchant/driver/customer roles and store approval.
- Idempotency key for order creation so network retries cannot create duplicate orders.
- Server-side delivery-fee rules and who receives the fee.
- Payment-provider integration before enabling electronic payment.
- Order notifications/realtime behavior and retry strategy.
- Driver commission/settlement ledger and limits, if commissions are part of launch.
- Privacy/retention rules for precise customer/driver location.
- Automated SQL concurrency tests and successful builds for all four apps.

## Tonight's completion criteria

Call a phase complete only when the relevant evidence exists: successful SQL application in a disposable environment, recorded role-based test results, successful Gradle builds, and an end-to-end test order through all relevant apps. GitHub files and draft PRs alone do not satisfy these criteria.
