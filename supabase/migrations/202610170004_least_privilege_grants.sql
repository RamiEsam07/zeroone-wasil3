-- WASIL least-privilege grants (draft; apply only after validating earlier migrations)
-- Restricts privileged account/store fields and prevents self-service store activation.

begin;

-- Profiles: ordinary clients may update only basic profile fields.
revoke update on public.profiles from authenticated;
grant update (full_name, phone, avatar_url) on public.profiles to authenticated;

-- Driver status is controlled by server-side RPCs. Clients may update only
-- vehicle details/location, not availability or commission balances.
revoke insert on public.driver_profiles from authenticated;
revoke update on public.driver_profiles from authenticated;
grant update (vehicle_type, latitude, longitude, last_location_at)
  on public.driver_profiles to authenticated;

-- Stores must be approved/activated through a trusted administrative path.
-- A new merchant-created store starts inactive; clients cannot set is_active
-- or change ownership during insert/update.
alter table public.stores alter column is_active set default false;
revoke insert, update on public.stores from authenticated;
grant insert (owner_id, category_id, name_ar, name_en, description, phone,
              image_url, address_text, latitude, longitude, is_open)
  on public.stores to authenticated;
grant update (category_id, name_ar, name_en, description, phone, image_url,
              address_text, latitude, longitude, is_open)
  on public.stores to authenticated;

-- Ensure clients can read order records only through the RLS participant policies.
grant select on public.orders, public.order_items to authenticated;

commit;
