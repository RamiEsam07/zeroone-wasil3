-- Smoke checks run only against the disposable PostgreSQL service in CI.
-- They do not connect to or modify the live Supabase project.

do $$
begin
  if (select count(*) from public.categories where slug in
      ('restaurants', 'cafes', 'groceries', 'bakeries', 'juices', 'pharmacy')) <> 6 then
    raise exception 'Default customer categories were not seeded';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'orders' and column_name = 'idempotency_key'
  ) then
    raise exception 'Order idempotency column is missing';
  end if;

  if has_function_privilege('anon', 'public.wasil_create_order(uuid, uuid, jsonb, uuid, text)', 'EXECUTE') then
    raise exception 'Anonymous users must not execute order creation';
  end if;

  if not has_function_privilege('authenticated', 'public.wasil_create_order(uuid, uuid, jsonb, uuid, text)', 'EXECUTE') then
    raise exception 'Authenticated users need the order RPC grant';
  end if;

  if has_function_privilege('anon', 'public.wasil_set_default_address(uuid)', 'EXECUTE') then
    raise exception 'Anonymous users must not change default addresses';
  end if;

  if not has_function_privilege('authenticated', 'public.wasil_set_default_address(uuid)', 'EXECUTE') then
    raise exception 'Authenticated customers need the default-address RPC grant';
  end if;

  if has_function_privilege('anon', 'public.wasil_admin_set_user_access(uuid, public.wasil_role, boolean)', 'EXECUTE') then
    raise exception 'Anonymous users must not execute admin provisioning';
  end if;

  if has_function_privilege('anon', 'public.wasil_list_available_orders()', 'EXECUTE') then
    raise exception 'Anonymous users must not list driver offers';
  end if;

  if not has_function_privilege('authenticated', 'public.wasil_list_available_orders()', 'EXECUTE') then
    raise exception 'Authenticated drivers need the limited available-order RPC';
  end if;

  if has_function_privilege('anon', 'public.wasil_admin_assign_driver(uuid, uuid)', 'EXECUTE')
     or has_function_privilege('anon', 'public.wasil_admin_set_driver_availability(uuid, boolean)', 'EXECUTE')
     or has_function_privilege('anon', 'public.wasil_admin_set_product_active(uuid, boolean)', 'EXECUTE') then
    raise exception 'Anonymous users must not execute admin operational RPCs';
  end if;

  if has_function_privilege('anon', 'public.wasil_auto_assign_ready_order(uuid)', 'EXECUTE')
     or has_function_privilege('anon', 'public.wasil_driver_release_assignment(uuid, text)', 'EXECUTE') then
    raise exception 'Anonymous users must not execute assignment lifecycle RPCs';
  end if;

  if not has_table_privilege('authenticated', 'public.platform_settings', 'SELECT') then
    raise exception 'Authenticated role needs table SELECT grant for admin settings RLS';
  end if;

  if has_table_privilege('anon', 'public.platform_settings', 'SELECT') then
    raise exception 'Anonymous users must not read platform settings';
  end if;

  if has_column_privilege('authenticated', 'public.products', 'is_active', 'INSERT')
     or has_column_privilege('authenticated', 'public.products', 'is_active', 'UPDATE') then
    raise exception 'Merchants must not directly set product activation';
  end if;

  if has_column_privilege('authenticated', 'public.stores', 'is_active', 'INSERT')
     or has_column_privilege('authenticated', 'public.stores', 'is_active', 'UPDATE') then
    raise exception 'Merchants must not directly set store activation';
  end if;

  if has_column_privilege('authenticated', 'public.driver_profiles', 'commission_balance', 'UPDATE')
     or has_column_privilege('authenticated', 'public.driver_profiles', 'is_available', 'UPDATE') then
    raise exception 'Drivers must not directly set availability or commission balance';
  end if;

  if has_column_privilege('authenticated', 'public.driver_profiles', 'profile_id', 'INSERT')
     or has_column_privilege('authenticated', 'public.driver_profiles', 'vehicle_type', 'INSERT') then
    raise exception 'Driver profile creation must use trusted admin provisioning';
  end if;

  if not exists (
    select 1 from public.platform_settings
    where id = true
      and commission_rate_percent = 0
      and base_delivery_fee = 0
      and fee_per_km = 0
      and tax_rate_percent = 0
      and auto_assign_drivers = false
  ) then
    raise exception 'Platform settings must start with safe unconfigured pricing and disabled auto-assignment';
  end if;

  if (
    select column_default from information_schema.columns
    where table_schema = 'public' and table_name = 'stores' and column_name = 'is_active'
  ) not like 'false%' then
    raise exception 'New stores must default to pending/inactive approval';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'orders'
      and column_name in ('customer_name_snapshot', 'customer_phone_snapshot')
    group by table_schema, table_name
    having count(*) = 2
  ) then
    raise exception 'Order customer contact snapshots are missing';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'products' and column_name = 'is_active'
  ) then
    raise exception 'Product admin activation column is missing';
  end if;
end;
$$;

insert into auth.users (id, phone, raw_user_meta_data) values
  ('00000000-0000-0000-0000-000000000001', '+249900000001', '{}'::jsonb),
  ('00000000-0000-0000-0000-000000000002', '+249900000002', '{}'::jsonb);

insert into public.addresses (
  customer_id, label, address_text, city, area, street, building_number,
  recipient_name, recipient_phone
) values
  ('00000000-0000-0000-0000-000000000001', 'Home', 'Customer one address', 'KHARTOUM', 'Area 1', 'Street 1', '1', 'Customer One', '+249900000001'),
  ('00000000-0000-0000-0000-000000000002', 'Home', 'Customer two address', 'KHARTOUM', 'Area 2', 'Street 2', '2', 'Customer Two', '+249900000002');

begin;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

do $$
declare
  v_visible_addresses integer;
begin
  select count(*) into v_visible_addresses from public.addresses;
  if v_visible_addresses <> 1 then
    raise exception 'RLS leak: customer can see % addresses; expected only their own', v_visible_addresses;
  end if;

  begin
    update public.profiles
       set role = 'admin'::public.wasil_role
     where id = '00000000-0000-0000-0000-000000000001';
    raise exception 'Security failure: customer changed their own role';
  exception
    when insufficient_privilege then null;
  end;
end;
$$;
rollback;
