# WASIL Shared Integration Contract
Status: review draft for the four isolated `ops/wasil-integration-audit` branches.
Last reviewed: 2026-10-19.

This document is the coordination contract for the customer, merchant/store, driver, and admin Android apps. It does not authorize merging branches or applying SQL migrations to a live Supabase project.

## Source of truth
- Supabase schema and server-side RPCs are authoritative for persisted order state, pricing, payment state, role, and activation.
- Each app may keep a role-specific UI model, but it must map explicitly to the shared database contract. Never write UI enum names directly to Supabase without a mapper.
- Order totals must come from the server-validated order record/RPC response. Platform commission is internal merchant/platform accounting and is not an extra customer charge.
- IDs are UUID strings from Supabase. Timestamps from the database are ISO-8601/timestamptz; UI epoch-millisecond fields require explicit conversion.
- Currency is SDG for the current Sudan launch direction. Do not show USD symbols in operational order screens.

## Canonical database enums (migration draft 202610170001)
Role values: `customer`, `merchant`, `driver`, `admin`.

Persisted order statuses:
- `placed`
- `confirmed`
- `preparing`
- `ready_for_pickup`
- `driver_assigned`
- `picked_up`
- `on_the_way`
- `delivered`
- `cancelled`

Persisted payment methods: `cash_on_delivery`, `electronic`.
Persisted payment statuses: `pending`, `paid`, `failed`, `refunded`.

## Status mapping rules
| Canonical Supabase value | Customer/admin UI concept | Merchant/store UI concept | Driver UI concept |
|---|---|---|---|
| `placed` | NEW / awaiting store | NEW / incoming | Not yet assigned |
| `confirmed` | ACCEPTED | ACCEPTED | Not yet assigned |
| `preparing` | PREPARING | PREPARING | Not yet assigned |
| `ready_for_pickup` | READY | READY | Pickup can be offered only when assigned by server logic |
| `driver_assigned` | Show assigned/dispatch state without pretending pickup occurred | Show driver assignment | New mission / assigned |
| `picked_up` | PICKED_UP | PICKED_UP / handed off | PICKED_UP |
| `on_the_way` | ON_THE_WAY | ON_THE_WAY | ON_THE_WAY |
| `delivered` | DELIVERED | DELIVERED | DELIVERED |
| `cancelled` | CANCELLED | CANCELLED | Remove/close assignment according to server response |

Legacy UI values `REJECTED` do not have a matching canonical database enum in the inspected schema. Do not write `REJECTED` to Supabase; represent a merchant refusal through the supported server-side transition/cancellation reason after confirming the RPC contract. The schema distinguishes cancellation but currently does not persist a separate rejected status. This is a known contract decision to resolve before release.

## Payment safety
- Launch checkout is cash on delivery only until an electronic provider is implemented and end-to-end tested.
- Do not mark an order as `paid` merely because a local UI model defaults to PAID.
- `electronic` is a broad schema value, not proof that Bankak/Fawry/card payments are connected.
- Store/driver/admin screens must display the persisted payment status, not infer it from the selected method.

## Shared order data requirements
Each role-specific mapper should preserve, when available:
- `id`, `order_number`, `customer_id`, `store_id`, `driver_id`
- customer/store names and phone snapshots used by the role UI
- item IDs/product IDs, name snapshots, unit price, quantity, and line total
- `subtotal`, `delivery_fee`, `service_fee`, `tax_amount`, `total`
- `platform_commission_amount` for merchant/admin reporting only
- `payment_method`, `payment_status`, `status`, `created_at`, `updated_at`
- delivery address snapshot and optional notes
Do not substitute demo fixtures for failed live requests without a clear demo/offline indicator.

## Language and direction contract
- Both Arabic and English are required in all four apps.
- Arabic: Arabic strings and RTL layout direction.
- English: English strings and LTR layout direction.
- Language toggles must change all visible labels, dialogs, errors, status names, navigation labels, and settings labels—not only the main screen.
- Store language preference persistently on device. Changing language must not log the user out or change account/order data.
- Database content uses `name_ar` and `name_en` where available; render the selected language with a sensible fallback to the other language when the chosen field is empty.
- Keep customer-facing brand spelling as **وصّل / WASIL** and preserve the approved deep-green/orange identity.

## Integration/release gates
1. Complete explicit status/payment/timestamp/fee mappers in each app and review diffs.
2. Build all four apps against their exact review-branch heads.
3. Test mappings with fixture rows covering every status, missing optional values, and both language settings.
4. Validate SQL migrations and RPCs only in a disposable Supabase/PostgreSQL environment.
5. After the four branches are unified and reviewed, the owner/team can integrate the apps, configure a test Supabase project, and perform end-to-end tests.
6. Live migration is a separate, later action requiring backup, a reviewed migration plan, and explicit owner approval.

## Current known mismatches from source review
- Customer/admin models use names such as `NEW`, `ACCEPTED`, and `READY`; database values use `placed`, `confirmed`, and `ready_for_pickup`.
- Database has `driver_assigned`, which is not a distinct value in every UI enum.
- Customer model offers Bankak and Fawry while the current secure order RPC/schema supports cash on delivery only.
- Store model defaults include `CREDIT_CARD` and `PAID`; verify these defaults and all live mappers before allowing them to reach persisted orders.
- Driver app has an Arabic/English toggle and localization strings; the other three apps require an equivalent complete, persistent language experience.
