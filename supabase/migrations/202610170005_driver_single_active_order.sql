-- WASIL driver availability and single-active-order guard (draft; validate before applying)
-- Prevents a driver from accepting concurrent orders and from manually marking
-- themselves available while an active delivery is assigned.

begin;

-- Availability is controlled by trusted RPCs, not direct client UPDATEs.
revoke update on public.driver_profiles from authenticated;
grant update (vehicle_type, latitude, longitude, last_location_at)
  on public.driver_profiles to authenticated;

create or replace function public.wasil_set_driver_availability(p_is_available boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_profile_id uuid;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if public.current_wasil_role() is distinct from 'driver'::public.wasil_role then
    raise exception 'Only driver accounts can change driver availability' using errcode = '42501';
  end if;

  select d.profile_id
    into v_profile_id
  from public.driver_profiles d
  where d.profile_id = v_uid
  for update;

  if not found then
    raise exception 'Driver profile not found' using errcode = '42501';
  end if;

  if p_is_available and exists (
    select 1
    from public.orders o
    where o.driver_id = v_uid
      and o.status in ('driver_assigned', 'picked_up', 'on_the_way')
  ) then
    raise exception 'Cannot become available while an active delivery is assigned' using errcode = '22023';
  end if;

  update public.driver_profiles
     set is_available = p_is_available,
         updated_at = now()
   where profile_id = v_uid;

  return true;
end;
$$;

revoke all on function public.wasil_set_driver_availability(boolean) from public, anon;
grant execute on function public.wasil_set_driver_availability(boolean) to authenticated;

-- Lock this driver's profile before checking availability. This serializes
-- concurrent accept attempts by the same driver.
create or replace function public.wasil_accept_order(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_profile_id uuid;
  v_available boolean;
  v_updated integer;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if public.current_wasil_role() is distinct from 'driver'::public.wasil_role then
    raise exception 'Only driver accounts can accept orders' using errcode = '42501';
  end if;

  select d.profile_id, d.is_available
    into v_profile_id, v_available
  from public.driver_profiles d
  where d.profile_id = v_uid
  for update;

  if not found or v_available is not true then
    raise exception 'Driver profile must exist and be available' using errcode = '42501';
  end if;

  if exists (
    select 1
    from public.orders o
    where o.driver_id = v_uid
      and o.status in ('driver_assigned', 'picked_up', 'on_the_way')
  ) then
    raise exception 'Driver already has an active delivery' using errcode = '22023';
  end if;

  -- Conditional update ensures only one driver can win the same ready order.
  update public.orders
     set driver_id = v_uid,
         status = 'driver_assigned',
         updated_at = now()
   where id = p_order_id
     and driver_id is null
     and status = 'ready_for_pickup';

  get diagnostics v_updated = row_count;

  if v_updated = 0 then
    return false;
  end if;

  update public.driver_profiles
     set is_available = false,
         updated_at = now()
   where profile_id = v_uid;

  return true;
end;
$$;

revoke all on function public.wasil_accept_order(uuid) from public, anon;
grant execute on function public.wasil_accept_order(uuid) to authenticated;

commit;

-- Required validation:
-- 1) One driver must not accept two orders concurrently.
-- 2) Two drivers racing for one order: exactly one succeeds.
-- 3) A driver with an active order cannot set availability to true.
-- 4) A driver can go available again after delivered/cancelled eligible flow.
-- 5) Merchant/admin/customer roles cannot call driver availability/accept RPCs.
