# WASIL Integration Endpoints Map
Status: source-code audit, not production verification.
Canonical contract: [WASIL_SHARED_CONTRACT.md](WASIL_SHARED_CONTRACT.md).

## Shared Supabase API
All apps should use the same Supabase project URL and publishable key, but each request must use the signed-in user's access token. The publishable key is public configuration, not permission. RLS and server-side role checks are mandatory.

### Customer app observed calls
- Read active stores/products/categories, customer addresses, own orders and order items through PostgREST.
- `POST /rest/v1/rpc/wasil_create_order` — server-authoritative checkout.
- `POST /rest/v1/rpc/wasil_set_default_address` — atomic default address.
- `POST /rest/v1/rpc/wasil_cancel_order` — server-validated cancellation.

### Merchant/store app observed calls
- Read categories, own store, products, orders and order items through PostgREST.
- `POST /rest/v1/rpc/wasil_merchant_update_order_status` — merchant order state transitions.
- `POST /rest/v1/rpc/wasil_cancel_order` — cancellation flow.
- The current repository also directly PATCHes store/product rows for some management actions. Confirm those exact columns are permitted by least-privilege grants/RLS and migrate any privileged state changes to reviewed RPCs where needed.

### Driver app observed calls
- Read own profile and driver profile.
- `POST /rest/v1/rpc/wasil_set_driver_availability`
- `POST /rest/v1/rpc/wasil_accept_order`
- `POST /rest/v1/rpc/wasil_driver_update_order_status`
- `POST /rest/v1/rpc/wasil_driver_release_assignment`
- `POST /rest/v1/rpc/wasil_driver_update_location`
- `POST /rest/v1/rpc/wasil_list_available_orders`
- Read active/history orders with nested store/address/items, subject to RLS.

### Admin app observed calls
- Read profiles, stores, categories, driver profiles, products and orders.
- Calls RPCs through its helper for privileged operations, including store approval/update and settings operations.
- The current repository also contains direct PATCH calls for profile/store changes. Review these against the least-privilege grants and prefer guarded RPCs for role, activation, approval and multi-column atomic changes.

## RPCs present in inspected migration drafts
- `wasil_create_order`
- `wasil_accept_order`
- `wasil_auto_assign_ready_order`
- `wasil_merchant_update_order_status`
- `wasil_cancel_order`
- `wasil_driver_update_order_status`
- `wasil_set_driver_availability`
- `wasil_list_available_orders`
- `wasil_driver_update_location`
- `wasil_driver_release_assignment`
- `wasil_admin_set_user_access`
- `wasil_admin_set_store_active`
- `wasil_admin_assign_driver`
- `wasil_admin_set_driver_availability`
- `wasil_admin_upsert_category`
- `wasil_admin_set_category_active`
- `wasil_admin_set_product_active`
- `wasil_admin_get_settings`
- `wasil_admin_update_settings`
- `wasil_set_default_address`
- `wasil_admin_update_store`

Function availability here means only that a function is present in a repository migration draft. It does not mean that the function exists in the live Supabase project.

## Integration sequence
1. Keep each Android app isolated; do not merge PRs yet.
2. Create/verify explicit Kotlin-to-database mappers for status, payment method/status, IDs and timestamps.
3. Resolve contract gaps: legacy `REJECTED` versus database `cancelled`; UI payment options versus cash-only checkout; `driver_assigned` versus UI state.
4. Review every direct PATCH/POST against RLS and grants; privileged state changes should use a server-checked RPC.
5. Run Android CI for exact current branch heads. The merchant PR's last recorded CI run for its PR head failed at duplicate named arguments in `StoreRepository.kt`; the current branch copy appears to have those duplicates removed, so refresh the PR head and rerun CI before claiming the fix is verified.
6. Validate migrations in a disposable Supabase/PostgreSQL environment, then connect all four apps to that test environment and test end-to-end.
7. Only after explicit approval, plan live migration with a backup and rollback path.

## Language integration
- Merchant/store app already has an English/Arabic switch and adjusts Compose layout direction.
- Driver app has Arabic/English string helpers and language state.
- Admin app has Arabic/English strings and dynamic layout direction.
- Customer app currently forces RTL and has no equivalent complete language switch in its root activity. Customer localization is a real implementation task, not yet complete.
- Language switching currently needs persistence and coverage audits in all apps; a toggle existing in the UI alone is not enough to call localization complete.
