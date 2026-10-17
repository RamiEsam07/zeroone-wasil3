-- WASIL secure order RPCs (draft; review before applying to Supabase)
-- Adds transactional order creation and atomic first-driver-wins acceptance.
-- This migration does not alter existing app screens or apply itself to Supabase.

begin;

create or replace function public.wasil_create_order(
  p_store_id uuid,
  p_address_id uuid,
  p_items jsonb,
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
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if public.current_wasil_role() is distinct from 'customer'::public.wasil_role then
    raise exception 'Only customer accounts can create orders' using errcode = '42501';
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
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer)
    where x.product_id is null or x.quantity is null or x.quantity < 1 or x.quantity > 100
  ) then
    raise exception 'Each item needs a valid product_id and quantity from 1 to 100' using errcode = '22023';
  end if;

  select count(*), count(distinct x.product_id)
    into v_count, v_distinct_count
  from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer);

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

  -- Lock all requested product rows in a stable order to avoid overselling and reduce deadlocks.
  for v_item in
    select p.id, p.price, p.name_ar, p.stock_quantity, x.quantity
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer)
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
  from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer)
  join public.products p on p.id = x.product_id
  where p.store_id = p_store_id and p.is_available and p.stock_quantity >= x.quantity;

  if v_count <> jsonb_array_length(p_items) then
    raise exception 'One or more products are missing, unavailable, out of stock, or belong to another store'
      using errcode = '22023';
  end if;

  -- Until delivery pricing is configured server-side, use a fixed zero fee rather than trusting the client.
  -- Electronic payment is intentionally not accepted until a payment provider is configured.
  insert into public.orders (
    customer_id, store_id, address_id, status, payment_method, payment_status,
    currency, subtotal, delivery_fee, total, customer_notes
  ) values (
    v_uid, p_store_id, p_address_id, 'placed', 'cash_on_delivery', 'pending',
    'SDG', v_subtotal, 0, v_subtotal, left(p_customer_notes, 1000)
  ) returning id into v_order_id;

  for v_item in
    select p.id, p.price, p.name_ar, x.quantity
    from jsonb_to_recordset(p_items) as x(product_id uuid, quantity integer)
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
      order_id, product_id, product_name_snapshot, unit_price, quantity
    ) values (
      v_order_id, v_item.id, v_item.name_ar, v_item.price, v_item.quantity
    );
  end loop;

  return v_order_id;
end;
$$;

revoke all on function public.wasil_create_order(uuid, uuid, jsonb, text) from public, anon;
grant execute on function public.wasil_create_order(uuid, uuid, jsonb, text) to authenticated;

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
