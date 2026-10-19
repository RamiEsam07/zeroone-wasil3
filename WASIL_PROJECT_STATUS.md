# WASIL project status — review branch

Last updated: 2026-10-19

This is the current source of truth for the review branch. It separates code changes from verified runtime behavior.

## Safety and branch state

- Working branch: **ops/wasil-integration-audit** (isolated operations/audit branch based on `fix/wasil-foundation-review`)
- Draft pull request: https://github.com/RamiEsam07/zeroone-wasil3/pull/2
- **main is unchanged; nothing has been merged.**
- SQL migrations are drafts and have not been applied to the live Supabase project.
- No Supabase secret/service-role key is stored in Android code or committed configuration.
- The customer app and three role-specific apps are being tracked in isolated `ops/wasil-integration-audit` branches. Android build workflows have been updated to trigger on these branches. The audit docs in each role repository record verified code paths and outstanding release gates. Do not infer a passing build from an empty status response; verify each workflow run against its exact head commit before merge. Repositories remain separate and no changes are merged.

## Cross-app operational audit — not yet release-approved

- Merchant: role-gated Google/Supabase sign-in, store onboarding actions and category loading are present in the inspected entry/view-model path. Live OAuth, RLS and store approval are not verified.
- Driver: Supabase-backed driver repository and location publication are present. Real-device permission/lifecycle behavior and order assignment are not verified.
- Admin: role-gated entry and Supabase-backed repository operations are present. An atomic store-edit RPC draft was added as migration `202610170008_admin_update_store_atomic.sql`, and the admin audit branch now calls it. Both sides must be validated and deployed together; no live migration has been applied. All RLS/RPC permissions remain release gates.
- CI: build workflows now include the operations branch. Latest run inspection found the customer/admin builds passing on their checked commits, the driver build passing, and the merchant build failing due to duplicate named arguments in StoreRepository.kt; that compile defect has been fixed on the audit branch and must be confirmed by a rerun.

## Latest cross-app change — draft, not production applied\n\n- Added migration `supabase/migrations/202610170008_admin_update_store_atomic.sql` with `wasil_admin_update_store(...)`. It updates store name, phone, address, open state, and approval state in one database transaction, checks active administrator privileges, requires an active merchant owner before approval, and blocks deactivation while active orders exist.\n- Admin app branch now calls this RPC instead of issuing a metadata PATCH followed by a separate activation RPC. This avoids the previously identified split-write failure mode once migration 008 is applied and verified.\n- Validation still required: run the migration smoke suite against disposable PostgreSQL, build the admin Android app at the new commit, and test both success and rejection cases. Do not apply to live Supabase yet.\n\n## Implemented in code — not yet fully runtime-verified

| Area | Current implementation | Verification still required |
|---|---|---|
| Sign-in | Native Google Credential Manager obtains an ID token and exchanges it for a Supabase Auth session. | Configure Google OAuth in Supabase/Google Cloud and complete a real device sign-in. |
| Session | AES-GCM encrypted session storage backed by Android Keystore; refresh near expiry on restore and before data calls; refresh-token rotation is serialized to prevent parallel requests reusing one rotating token; sign-out clears local session. REST requests send the publishable key only in `apikey` and send `Authorization: Bearer` only for a real user access token. | Test app restart, expiry, revoked refresh token, offline/network failures and sign-out on a device. |
| Role gate | Customer app checks the server-owned profile role and active flag before showing customer screens. | Test customer, inactive, merchant, driver and admin accounts. |
| Customer catalog | Supabase API adapter reads active/open stores and available products. | Seed test stores/products and verify mapping, filters, images and empty states. |
| Addresses | Reads/creates customer addresses and uses an RPC to set the default atomically. | Test owner isolation, default switching, validation and deletion behavior. |
| Orders | Server RPC creates orders with authoritative prices, inventory locking, snapshots and idempotency. Delivery fee uses the greater of the configured platform base fee and the store fee, plus the configured per-kilometer rate. Tax and service fee currently default to zero. Platform commission is calculated from the verified subtotal and recorded per order for merchant/admin visibility; it does not increase the customer payable total. Merchant/admin order detail now separates commission, tax and service fee. The checkout UI must be verified against non-zero configured fees before those rates are enabled. | Validate fee policy, stock races, totals and rollback in disposable data. |
| Retry safety | Client retains an idempotency key for retries of the same order payload; SQL RPC serializes duplicate requests for the same customer/key. | Prove retry and concurrency behavior with tests. |
| Cancellation | Server RPC validates allowed states and restores stock in the same transaction. | Test allowed/denied roles and repeated cancellation. |
| Tracking and dispatch | Customer order/history views poll order state every 15 seconds. Driver app requests foreground location only while available or on an active delivery and publishes coordinates through a server-timestamped RPC. Merchant onboarding/profile accepts store coordinates; auto-assignment requires fresh driver coordinates, store coordinates, availability and radius. | Verify location permissions and actual coordinates on a device; test dispatch with multiple drivers and decide on Supabase Realtime/push notifications. |
| Admin setup | RPCs provision role/activation and approve/deactivate stores; address-default RPC and least-privilege grants are included. | First admin account is not yet created. After owner signs in once with Google and confirms the intended account UUID in Supabase Auth/Profiles, bootstrap only that exact account using the guarded `supabase/bootstrap_first_admin.sql.example` in the trusted SQL Editor after the reviewed migrations are validated and approved. Never execute from the client app. |
| CI | Android debug-build workflows run on all four isolated role-app review branches. The customer/database repo applies all eight SQL migrations to disposable PostgreSQL and runs security/functional smoke tests, including order idempotency, server pricing, coordinate-based radius rejection, driver auto-assignment/location, default-address updates and one-time stock restoration on cancellation. | Before merge, inspect the latest completed checks for the exact head commit of each branch. Green builds and disposable-database tests do not prove live Supabase, Google OAuth, or device-level end-to-end behavior. |

## Database migration order

1. 202610170001_initial_wasil_schema.sql
2. 202610170002_secure_order_rpcs.sql
3. 202610170003_driver_lifecycle_and_cancellation.sql
4. 202610170004_least_privilege_grants.sql
5. 202610170005_driver_single_active_order.sql
6. 202610170006_admin_provisioning.sql
7. 202610170007_address_default_rpc.sql
8. 202610170008_admin_update_store_atomic.sql

Apply all eight only to a disposable Supabase/PostgreSQL environment first, in order, and stop at the first error. The migration smoke workflow is now configured to run on `ops/wasil-integration-audit`. The GitHub smoke workflow uses a minimal mock Auth schema; it does not prove every Supabase Auth behavior.

## Agreed operating decisions to preserve

- Launch in one city/area first, then expand.
- Delivery assignment should consider location, driver availability and vehicle type.
- Cash on delivery is the launch payment method; electronic payments stay disabled until a provider is configured and tested.
- Weekly driver commission settlement with a maximum outstanding balance cap is a planned rule; the cap amount is not decided and must be based on real operating data.
- Preserve the approved WASIL deep-green/orange brand direction and existing UI direction; do not replace the agreed logo without approval.

## Blockers before merge or launch

- Latest verified review commit: `f96b703bad9d5cf83620632b9c3f5f06adf41a2e` (2026-10-19). GitHub Actions Android build check run 255 **passed**, and Supabase migration smoke check run 218 **passed**. Both applied/compiled the reviewed branch; SQL ran against disposable PostgreSQL, not the live Supabase project. Any later commit must be rechecked before merge.
- Apply all seven migrations to a disposable Supabase project (not the live project) and test role/RLS, order stock races, idempotency, cancellation and driver concurrency; CI currently validates them against a disposable PostgreSQL/Auth test schema.
- Owner reports Google OAuth is configured; still run end-to-end sign-in/session refresh/sign-out on a real device.
- Test customer catalog, address, order, cancellation and tracking flows against real test records.
- Review the latest builds for merchant, driver and admin role-app branches and run end-to-end tests for onboarding, store/product approval, driver acceptance/status changes and admin provisioning.
- Approve the server-side delivery-fee, tax and service-fee policy. Initial platform fee/tax defaults are zero, and the configured store delivery fee is honored. Do not enable non-zero platform fees until checkout displays the same server-calculated quote.
- Add push notifications/Realtime, driver commission ledger/settlement, location privacy/retention and payment-provider integration as launch requirements. Location capture and server-side update are now wired, but must be tested on real devices before enabling location-based dispatch.
