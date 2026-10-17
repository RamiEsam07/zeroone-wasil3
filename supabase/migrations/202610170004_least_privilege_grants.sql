-- WASIL least-privilege grants (draft; apply only after validating earlier migrations)
-- Removes broad table-level UPDATE grants that could let a client change privileged fields.

begin;

revoke update on public.profiles from authenticated;
grant update (full_name, phone, avatar_url) on public.profiles to authenticated;

revoke update on public.driver_profiles from authenticated;
grant update (vehicle_type, is_available, latitude, longitude, last_location_at)
  on public.driver_profiles to authenticated;

-- Ensure clients can read order records only through the RLS participant policies.
grant select on public.orders, public.order_items to authenticated;

commit;
