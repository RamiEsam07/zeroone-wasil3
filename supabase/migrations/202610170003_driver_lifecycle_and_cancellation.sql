-- WASIL driver lifecycle and safe cancellation (draft; validate before applying)
-- Cancellation restores stock in the same transaction and can only succeed once.
-- Driver lifecycle transitions are restricted to the assigned driver (or admin).

begin;

create or replace function public.wasil_cancel_order(
  p_order_id uuid,
  p_reason text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_role public.wasil_role;
  v_store_id uuid;
  v_customer_id uuid;
  v_driver_id uuid;
  v_status public.wasil_order_status;
  v_allowed boolean := false;
  v_item record;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  v_role := public.current_wasil_role();
  if v_role is null then
    raise exception 'Profile role not found' using errcode = '42501';
  end if;

  select o.store_id, o.customer_id, o.driver_id, o.status
    into v_store_id, v_customer_id, v_driver_id, v_status
  from public.orders o
  where o.id = p_order_id
  for update;

  if not found then
    return false;
  end if;

  if public.is_wasil_admin() then
    v_allowed := true;
  elsif v_role = 'customer'::public.wasil_role
        and v_customer_id = v_uid
        and v_status in ('placed', 'confirmed') then
    v_allowed := true;
  elsif v_role = 'merchant'::public.wasil_role
        and public.owns_wasil_store(v_store_id)
        and v_status in ('placed', 'confirmed', 'preparing') then
    v_allowed := true;
  end if;

  if not v_allowed then
    raise exception 'You are not allowed to cancel this order in its current state' using errcode = '42501';
  end if;

  if v_status in ('delivered', 'cancelled', 'picked_up', 'on_the_way') then
    raise exception 'This order can no longer be cancelled' using errcode = '22023';
  end if;

  update public.orders
     set status = 'cancelled',
         cancellation_reason = left(coalesce(p_reason, ''), 500),
         updated_at = now()
   where id = p_order_id;

  -- Restore only products still present. The order row lock + state guard ensures one restoration.
  for v_item in
    select oi.product_id, oi.quantity
    from public.order_items oi
    where oi.order_id = p_order_id and oi.product_id is not null
    order by oi.product_id
  loop
    update public.products
       set stock_quantity = stock_quantity + v_item.quantity,
           updated_at = now()
     where id = v_item.product_id;
  end loop;

  if v_driver_id is not null then
    update public.driver_profiles
       set is_available = true, updated_at = now()
     where profile_id = v_driver_id;
  end if;

  return true;
end;
$$;

revoke all on function public.wasil_cancel_order(uuid, text) from public, anon;
grant execute on function public.wasil_cancel_order(uuid, text) to authenticated;

create or replace function public.wasil_driver_update_order_status(
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
  v_current public.wasil_order_status;
  v_driver_id uuid;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if public.current_wasil_role() is distinct from 'driver'::public.wasil_role
     and not public.is_wasil_admin() then
    raise exception 'Driver or admin account required' using errcode = '42501';
  end if;

  select o.driver_id, o.status
    into v_driver_id, v_current
  from public.orders o
  where o.id = p_order_id
  for update;

  if not found then
    return false;
  end if;

  if not public.is_wasil_admin() and v_driver_id is distinct from v_uid then
    raise exception 'This order is not assigned to you' using errcode = '42501';
  end if;

  if not (
    (v_current = 'driver_assigned' and p_next_status = 'picked_up')
    or (v_current = 'picked_up' and p_next_status = 'on_the_way')
    or (v_current = 'on_the_way' and p_next_status = 'delivered')
  ) then
    raise exception 'Invalid driver order status transition' using errcode = '22023';
  end if;

  update public.orders
     set status = p_next_status,
         delivered_at = case when p_next_status = 'delivered' then now() else delivered_at end,
         updated_at = now()
   where id = p_order_id;

  if p_next_status = 'delivered' then
    update public.driver_profiles
       set is_available = true, updated_at = now()
     where profile_id = v_driver_id;
  end if;

  return true;
end;
$$;

revoke all on function public.wasil_driver_update_order_status(uuid, public.wasil_order_status) from public, anon;
grant execute on function public.wasil_driver_update_order_status(uuid, public.wasil_order_status) to authenticated;

commit;

-- Validation checklist:
-- * Test cancellation by customer, merchant, driver, and admin against allowed/denied states.
-- * Confirm stock is restored exactly once after cancellation and never after pickup.
-- * Race-test two drivers accepting the same ready order.
-- * Verify a driver cannot update another driver's order.
-- * Validate transaction rollback if any order item/product update fails.
