-- WASIL secure order RPCs (draft; review before applying to Supabase)
-- Adds transactional order creation and atomic first-driver-wins acceptance.
-- This migration does not alter existing app screens or apply itself to Supabase.

begin;

create or replace function public.wasil_create_order(
  p_store_id uuid,
  p_address_id uuid,
  p_items jsonb,
  p_idempotency_key uuid,
  p_customer_notes text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_order_id uuid;
  v_item record;
  v_count integer;
  v_distinct_count integer;
  v_subtotal numeric(12,2) := 0;
  v_delivery_fee numeric(12,2) := 0;
  v_service_fee numeric(12,2) := 0;
  v_tax_amount numeric(12,2) := 0;
  v_distance_km numeric(8,2) := 0;
  v_store_lat double precision;
  v_store_lon double precision;
  v_address_lat double precision;
  v_address_lon double precision;
  v_settings public.platform_settings%rowtype;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
  end if;

  if p_idempotency_key is null then
    raise exception 'An idempotency key is required' using errcode = '22023';
  end if;

  if public.current_wasil_role() is distinct from 'customer'::public.wasil_role then
    raise exception 'Only customer accounts can create orders' using errcode = '42501';
  end if;

  -- Serialize retries for this customer/key before touching stock or creating an order.
  perform pg_advisory_xact_lock(hashtextextended(v_uid::text || ':' || p_idempotency_key::text, 0));
  select o.id into v_order_id
  from public.orders o
  where o.customer_id = v_uid and o.idempotency_key = p_idempotency_key;
  if found then
    return v_order_id;
  end if;

  if p_store_id is null or p_address_id is null then
    raise exception 'Store and delivery address are required' using errcode = '22023';
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0
     or jsonb_array_length(p_items) > 50 then
    raise exception 'Items must be a non-empty array with at most 50 entries' using errcode = '22023';
  end if;

  if exists (
    select 1
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer, special_instructions text)
    where x.product_id is null or x.quantity is null or x.quantity < 1 or x.quantity > 100
  ) then
    raise exception 'Each item needs a valid product_id and quantity from 1 to 100' using errcode = '22023';
  end if;

  select count(*), count(distinct x.product_id)
    into v_count, v_distinct_count
  from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer, special_instructions text);

  if v_count <> v_distinct_count then
    raise exception 'Duplicate product IDs are not allowed; combine their quantities' using errcode = '22023';
  end if;

  if not exists (
    select 1 from public.addresses a
    where a.id = p_address_id and a.customer_id = v_uid
  ) then
    raise exception 'Delivery address not found for this customer' using errcode = '42501';
  end if;

  if not exists (
    select 1 from public.stores s
    where s.id = p_store_id and s.is_active and s.is_open
  ) then
    raise exception 'Store is not currently accepting orders' using errcode = '22023';
  end if;

  select * into v_settings from public.platform_settings where id = true;
  select s.latitude, s.longitude, a.latitude, a.longitude
    into v_store_lat, v_store_lon, v_address_lat, v_address_lon
  from public.stores s
  join public.addresses a on a.id = p_address_id
  where s.id = p_store_id;

  if v_store_lat is not null and v_store_lon is not null
     and v_address_lat is not null and v_address_lon is not null then
    v_distance_km := round((
      acos(least(1.0, greatest(-1.0,
        sin(radians(v_store_lat)) * sin(radians(v_address_lat))
        + cos(radians(v_store_lat)) * cos(radians(v_address_lat))
        * cos(radians(v_address_lon - v_store_lon))
      ))) * 6371.0
    )::numeric, 2);
    if v_distance_km > v_settings.max_delivery_radius_km then
      raise exception 'Delivery address is outside the configured service radius' using errcode = '22023';
    end if;
  end if;

  -- Lock all requested product rows in a stable order to avoid overselling and reduce deadlocks.
  for v_item in
    select p.id, p.price, p.name_ar, p.stock_quantity, x.quantity, x.special_instructions
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer, special_instructions text)
    join public.products p on p.id = x.product_id
    where p.store_id = p_store_id
    order by p.id
    for update of p
  loop
    if not exists (
      select 1 from public.products p2
      where p2.id = v_item.id
        and p2.store_id = p_store_id
        and p2.is_available
        and p2.stock_quantity >= v_item.quantity
    ) then
      raise exception 'A requested product is unavailable or has insufficient stock' using errcode = '22023';
    end if;
    v_subtotal := v_subtotal + (v_item.price * v_item.quantity);
  end loop;

  select count(*) into v_count
  from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer, special_instructions text)
  join public.products p on p.id = x.product_id
  where p.store_id = p_store_id and p.is_available and p.stock_quantity >= x.quantity;

  if v_count <> jsonb_array_length(p_items) then
    raise exception 'One or more products are missing, unavailable, out of stock, or belong to another store'
      using errcode = '22023';
  end if;

  v_delivery_fee := round(v_settings.base_delivery_fee + (v_distance_km * v_settings.fee_per_km), 2);
  v_service_fee := 0;
  v_tax_amount := round(v_subtotal * v_settings.tax_rate_percent / 100, 2);

  -- All prices are calculated from trusted server-side settings and product rows.
  -- Electronic payment is intentionally not accepted until a payment provider is configured.
  insert into public.orders (
    customer_id, store_id, address_id, status, payment_method, payment_status,
    currency, subtotal, delivery_fee, service_fee, tax_amount, distance_km, total, customer_notes, idempotency_key,
    customer_name_snapshot, customer_phone_snapshot,
    store_name_snapshot, store_phone_snapshot, store_image_snapshot,
    delivery_address_snapshot, recipient_name_snapshot, recipient_phone_snapshot
  ) values (
    v_uid, p_store_id, p_address_id, 'placed', 'cash_on_delivery', 'pending',
    'SDG', v_subtotal, v_delivery_fee, v_service_fee, v_tax_amount, v_distance_km,
    v_subtotal + v_delivery_fee + v_service_fee + v_tax_amount,
    left(p_customer_notes, 1000), p_idempotency_key,
    coalesce((select p.full_name from public.profiles p where p.id = v_uid), ''),
    coalesce((select p.phone from public.profiles p where p.id = v_uid), ''),
    (select s.name_ar from public.stores s where s.id = p_store_id),
    (select s.phone from public.stores s where s.id = p_store_id),
    (select s.image_url from public.stores s where s.id = p_store_id),
    (select a.address_text from public.addresses a where a.id = p_address_id),
    (select a.recipient_name from public.addresses a where a.id = p_address_id),
    (select a.recipient_phone from public.addresses a where a.id = p_address_id)
  ) returning id into v_order_id;

  for v_item in
    select p.id, p.price, p.name_ar, x.quantity, x.special_instructions
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer, special_instructions text)
    join public.products p on p.id = x.product_id
    where p.store_id = p_store_id
    order by p.id
  loop
    update public.products
      set stock_quantity = stock_quantity - v_item.quantity
      where id = v_item.id and stock_quantity >= v_item.quantity;

    if not found then
      raise exception 'Stock changed while creating the order; please retry' using errcode = '40001';
    end if;

    insert into public.order_items (
      order_id, product_id, product_name_snapshot, special_instructions, unit_price, quantity
    ) values (
      v_order_id, v_item.id, v_item.name_ar, left(coalesce(v_item.special_instructions, ''), 500), v_item.price, v_item.quantity
    );
  end loop;

  return v_order_id;
end;
$$;

revoke all on function public.wasil_create_order(uuid, uuid, jsonb, uuid, text) from public, anon;
grant execute on function public.wasil_create_order(uuid, uuid, jsonb, uuid, text) to authenticated;

create or replace function public.wasil_accept_order(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
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

  if not exists (
    select 1 from public.driver_profiles d
    where d.profile_id = v_uid and d.is_available
  ) then
    raise exception 'Driver profile must exist and be available' using errcode = '42501';
  end if;

  -- The conditional update is the concurrency gate: only one driver can change an unassigned ready order.
  update public.orders
     set driver_id = v_uid, status = 'driver_assigned', updated_at = now()
   where id = p_order_id
     and driver_id is null
     and status = 'ready_for_pickup';

  get diagnostics v_updated = row_count;

  if v_updated = 0 then
    return false;
  end if;

  update public.driver_profiles
     set is_available = false, updated_at = now()
   where profile_id = v_uid;

  return true;
end;
$$;

revoke all on function public.wasil_accept_order(uuid) from public, anon;
grant execute on function public.wasil_accept_order(uuid) to authenticated;

-- Optional automatic assignment: choose the nearest available active driver,
-- using vehicle type as a deterministic tie-breaker. If location data is absent,
-- prefer the most recently reported driver location and leave the order unassigned
-- when no eligible driver exists.
create or replace function public.wasil_auto_assign_ready_order(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $wasil_auto_assign_ready_order$
declare
  v_uid uuid := auth.uid();
  v_role public.wasil_role;
  v_store_id uuid;
  v_order_status public.wasil_order_status;
  v_existing_driver uuid;
  v_store_lat double precision;
  v_store_lon double precision;
  v_driver_id uuid;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
  end if;
  v_role := public.current_wasil_role();
  if v_role not in ('merchant'::public.wasil_role, 'admin'::public.wasil_role) then
    raise exception 'Merchant or admin account required' using errcode = '42501';
  end if;

  select o.store_id, o.status, o.driver_id, s.latitude, s.longitude
    into v_store_id, v_order_status, v_existing_driver, v_store_lat, v_store_lon
  from public.orders o
  join public.stores s on s.id = o.store_id
  where o.id = p_order_id
  for update of o;

  if not found or v_order_status <> 'ready_for_pickup' or v_existing_driver is not null then
    return false;
  end if;
  if v_role = 'merchant'::public.wasil_role and not public.owns_wasil_store(v_store_id) then
    raise exception 'This order does not belong to your store' using errcode = '42501';
  end if;
  if not coalesce((select ps.auto_assign_drivers from public.platform_settings ps where ps.id = true), false) then
    return false;
  end if;

  select d.profile_id into v_driver_id
  from public.driver_profiles d
  join public.profiles p on p.id = d.profile_id
  where p.role = 'driver'::public.wasil_role
    and p.is_active
    and d.is_available
    and not exists (
      select 1 from public.orders active_order
      where active_order.driver_id = d.profile_id
        and active_order.status in ('driver_assigned', 'picked_up', 'on_the_way')
    )
  order by
    case
      when v_store_lat is not null and v_store_lon is not null
       and d.latitude is not null and d.longitude is not null
      then acos(least(1.0, greatest(-1.0,
        sin(radians(v_store_lat)) * sin(radians(d.latitude))
        + cos(radians(v_store_lat)) * cos(radians(d.latitude))
        * cos(radians(d.longitude - v_store_lon))
      ))) * 6371.0
      else null
    end asc nulls last,
    case d.vehicle_type
      when 'motorcycle' then 0
      when 'car' then 1
      when 'van' then 2
      when 'bicycle' then 3
      else 4
    end,
    d.last_location_at desc nulls last,
    d.created_at
  limit 1
  for update of d skip locked;

  if v_driver_id is null then
    return false;
  end if;

  update public.orders
     set driver_id = v_driver_id,
         status = 'driver_assigned',
         assigned_at = now(),
         updated_at = now()
   where id = p_order_id
     and driver_id is null
     and status = 'ready_for_pickup';
  if not found then
    return false;
  end if;

  update public.driver_profiles
     set is_available = false, updated_at = now()
   where profile_id = v_driver_id;
  return true;
end;
$wasil_auto_assign_ready_order$;

revoke all on function public.wasil_auto_assign_ready_order(uuid) from public, anon;
grant execute on function public.wasil_auto_assign_ready_order(uuid) to authenticated;

-- Merchant-only forward status transitions. Cancellation is deliberately excluded until a stock-restoration RPC is implemented. A merchant cannot assign drivers or mark an order delivered.
create or replace function public.wasil_merchant_update_order_status(
  p_order_id uuid,
  p_next_status public.wasil_order_status
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_store_id uuid;
  v_current public.wasil_order_status;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
  end if;

  if public.current_wasil_role() is distinct from 'merchant'::public.wasil_role
     and not public.is_wasil_admin() then
    raise exception 'Merchant or admin account required' using errcode = '42501';
  end if;

  select o.store_id, o.status into v_store_id, v_current
  from public.orders o
  where o.id = p_order_id
  for update;

  if v_store_id is null then
    return false;
  end if;

  if not public.is_wasil_admin() and not public.owns_wasil_store(v_store_id) then
    raise exception 'This order does not belong to your store' using errcode = '42501';
  end if;

  if not (
    (v_current = 'placed' and p_next_status = 'confirmed')
    or (v_current = 'confirmed' and p_next_status = 'preparing')
    or (v_current = 'preparing' and p_next_status = 'ready_for_pickup')
  ) then
    raise exception 'Invalid merchant order status transition' using errcode = '22023';
  end if;

  update public.orders
    set status = p_next_status, updated_at = now()
    where id = p_order_id;

  if p_next_status = 'ready_for_pickup'::public.wasil_order_status then
    perform public.wasil_auto_assign_ready_order(p_order_id);
  end if;

  return true;
end;
$$;

revoke all on function public.wasil_merchant_update_order_status(uuid, public.wasil_order_status) from public, anon;
grant execute on function public.wasil_merchant_update_order_status(uuid, public.wasil_order_status) to authenticated;

commit;

-- Review checklist before applying:
-- 1) Verify function behavior in a disposable Supabase project / local PostgreSQL first.
-- 2) Seed and assign merchant/driver roles only through a trusted admin process.
-- 3) Configure delivery fee server-side before charging one; this draft uses zero.
-- 4) Add driver status-transition RPCs (picked_up/on_the_way/delivered) and cancellation/refund rules.
-- 5) Add idempotency protection before enabling client retries for order creation.
