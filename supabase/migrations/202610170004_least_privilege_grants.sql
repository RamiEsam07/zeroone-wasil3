-- WASIL least-privilege grants (draft; apply only after validating earlier migrations)
-- Restricts privileged account/store fields and prevents self-service store activation.

begin;

-- Profiles: ordinary clients may update only basic profile fields.
revoke update on public.profiles from authenticated;
grant update (full_name, phone, avatar_url) on public.profiles to authenticated;

-- Driver profile creation and availability are controlled by trusted provisioning/RPCs.
-- Drivers cannot create a profile row or set server-managed availability themselves.
revoke all on public.driver_profiles from authenticated;
revoke insert (profile_id, vehicle_type) on public.driver_profiles from authenticated;
grant select on public.driver_profiles to authenticated;
grant update (vehicle_type)
  on public.driver_profiles to authenticated;

-- Merchants may maintain product content, stock and availability, but only the
-- trusted admin RPC can activate/deactivate a product for customer discovery.
alter table public.products alter column is_active set default false;
revoke insert, update on public.products from authenticated;
grant insert (store_id, name_ar, name_en, description, image_url, category, price,
              stock_quantity, is_available)
  on public.products to authenticated;
grant update (name_ar, name_en, description, image_url, category, price,
              stock_quantity, is_available)
  on public.products to authenticated;

-- Stores must be approved/activated through a trusted administrative path.
-- A new merchant-created store starts inactive. RLS requires owner_id to equal
-- auth.uid() on insert; ordinary clients cannot change owner_id or set is_active.
alter table public.stores alter column is_active set default false;
revoke insert, update on public.stores from authenticated;
grant insert (owner_id, category_id, name_ar, name_en, description, phone,
              image_url, address_text, latitude, longitude, delivery_fee, min_order_amount, is_open)
  on public.stores to authenticated;
grant update (category_id, name_ar, name_en, description, phone, image_url,
              address_text, latitude, longitude, delivery_fee, min_order_amount, is_open)
  on public.stores to authenticated;

-- Ensure clients can read order records only through the RLS participant policies.
grant select on public.orders, public.order_items to authenticated;

commit;
