-- WASIL driver availability and single-active-order guard (draft; validate before applying)
-- Prevents a driver from accepting concurrent orders and from manually marking
-- themselves available while an active delivery is assigned.

begin;

-- Driver profile creation is performed by the trusted admin provisioning RPC.
-- Drivers may update vehicle_type directly; location, availability and commission are RPC-controlled.
revoke insert on public.driver_profiles from authenticated;
revoke update on public.driver_profiles from authenticated;
grant update (vehicle_type)
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

  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
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

  if exists (
    select 1
    from public.orders o
    where o.driver_id = v_uid
      and o.status in ('driver_assigned', 'picked_up', 'on_the_way')
  ) then
    if p_is_available then
      raise exception 'Cannot become available while an active delivery is assigned' using errcode = '22023';
    else
      raise exception 'Cannot go offline while an active delivery is assigned' using errcode = '22023';
    end if;
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

  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
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

-- Return only safe, pickup-stage fields for unassigned drivers. Contact and delivery-address
-- details remain protected by the orders/addresses RLS policies until the order is assigned.
create or replace function public.wasil_list_available_orders()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $available_orders$
declare
  v_uid uuid := auth.uid();
  v_result jsonb;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active()
     or public.current_wasil_role() is distinct from 'driver'::public.wasil_role then
    raise exception 'Active driver account required' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.driver_profiles d
    where d.profile_id = v_uid and d.is_available
  ) then
    return '[]'::jsonb;
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', o.id,
        'order_number', o.order_number,
        'store_id', o.store_id,
        'status', o.status,
        'payment_method', o.payment_method,
        'currency', o.currency,
        'subtotal', o.subtotal,
        'delivery_fee', o.delivery_fee,
        'service_fee', o.service_fee,
        'total', o.total,
        'placed_at', o.placed_at,
        'store', jsonb_build_object(
          'id', s.id,
          'name_ar', s.name_ar,
          'name_en', s.name_en,
          'phone', s.phone,
          'address_text', s.address_text,
          'latitude', s.latitude,
          'longitude', s.longitude
        ),
        'order_items', coalesce((
          select jsonb_agg(jsonb_build_object(
            'id', oi.id,
            'product_name_snapshot', oi.product_name_snapshot,
            'quantity', oi.quantity,
            'unit_price', oi.unit_price,
            'special_instructions', oi.special_instructions
          ) order by oi.id)
          from public.order_items oi
          where oi.order_id = o.id
        ), '[]'::jsonb)
      ) order by o.placed_at
    ),
    '[]'::jsonb
  )
  into v_result
  from public.orders o
  join public.stores s on s.id = o.store_id
  where o.status = 'ready_for_pickup'
    and o.driver_id is null
    and s.is_active
    and s.is_open;

  return v_result;
end;
$available_orders$;
revoke all on function public.wasil_list_available_orders() from public, anon;
grant execute on function public.wasil_list_available_orders() to authenticated;

create or replace function public.wasil_driver_update_location(
  p_latitude double precision,
  p_longitude double precision
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $wasil_driver_update_location$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active()
     or public.current_wasil_role() is distinct from 'driver'::public.wasil_role then
    raise exception 'Active driver account required' using errcode = '42501';
  end if;
  if p_latitude is null or p_latitude < -90 or p_latitude > 90
     or p_longitude is null or p_longitude < -180 or p_longitude > 180 then
    raise exception 'Valid latitude and longitude are required' using errcode = '22023';
  end if;

  update public.driver_profiles
     set latitude = p_latitude,
         longitude = p_longitude,
         last_location_at = now(),
         updated_at = now()
   where profile_id = v_uid;
  if not found then
    raise exception 'Driver profile must be provisioned by an administrator' using errcode = '42501';
  end if;
  return true;
end;
$wasil_driver_update_location$;

revoke all on function public.wasil_driver_update_location(double precision, double precision) from public, anon;
grant execute on function public.wasil_driver_update_location(double precision, double precision) to authenticated;

commit;

-- Required validation:
-- 1) One driver must not accept two orders concurrently.
-- 2) Two drivers racing for one order: exactly one succeeds.
-- 3) A driver with an active order cannot set availability to true.
-- 4) A driver can go available again after delivered/cancelled eligible flow.
-- 5) Merchant/admin/customer roles cannot call driver availability/accept RPCs.
