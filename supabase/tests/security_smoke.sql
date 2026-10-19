-- Smoke checks run only against the disposable PostgreSQL service in CI.
-- They do not connect to or modify the live Supabase project.

do $$
declare
  v_unprotected_tables text;
begin
  select string_agg(required.table_name, ', ' order by required.table_name)
    into v_unprotected_tables
  from (values
    ('profiles'), ('categories'), ('stores'), ('products'),
    ('addresses'), ('orders'), ('order_items'), ('driver_profiles'),
    ('platform_settings')
  ) as required(table_name)
  left join pg_class c on c.relname = required.table_name
  left join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
  where c.oid is null or n.oid is null or not c.relrowsecurity;

  if v_unprotected_tables is not null then
    raise exception 'RLS must be enabled on all exposed tables; missing/unprotected: %', v_unprotected_tables;
  end if;

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

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'profiles' and column_name = 'email'
  ) then
    raise exception 'Profile email column for admin account identification is missing';
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

  if has_column_privilege('authenticated', 'public.driver_profiles', 'latitude', 'UPDATE')
     or has_column_privilege('authenticated', 'public.driver_profiles', 'longitude', 'UPDATE')
     or has_column_privilege('authenticated', 'public.driver_profiles', 'last_location_at', 'UPDATE') then
    raise exception 'Drivers must update location through the server-timestamped RPC';
  end if;

  if has_function_privilege('anon', 'public.wasil_driver_update_location(double precision, double precision)', 'EXECUTE')
     or not has_function_privilege('authenticated', 'public.wasil_driver_update_location(double precision, double precision)', 'EXECUTE') then
    raise exception 'Driver location RPC grants are incorrect';
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
    where table_schema = 'public' and table_name = 'orders'
      and column_name in ('assigned_at', 'picked_up_at', 'delivered_at')
    group by table_schema, table_name
    having count(*) = 3
  ) then
    raise exception 'Driver lifecycle timestamps are missing';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'products' and column_name = 'is_active'
  ) then
    raise exception 'Product admin activation column is missing';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'orders' and column_name = 'platform_commission_amount'
  ) then
    raise exception 'Server-calculated platform commission column is missing';
  end if;

  if has_column_privilege('authenticated', 'public.products', 'is_active', 'INSERT')
     or has_column_privilege('authenticated', 'public.products', 'is_active', 'UPDATE') then
    raise exception 'Merchants must not set or change the product moderation flag';
  end if;

  if (
    select column_default from information_schema.columns
    where table_schema = 'public' and table_name = 'products' and column_name = 'is_active'
  ) not like 'false%' then
    raise exception 'New products must remain inactive until admin approval';
  end if;
end;
$$;

insert into auth.users (id, phone, raw_user_meta_data) values
  ('00000000-0000-0000-0000-000000000001', '+249900000001', '{}'::jsonb),
  ('00000000-0000-0000-0000-000000000002', '+249900000002', '{}'::jsonb),
  ('00000000-0000-0000-0000-000000000003', '+249900000003', '{}'::jsonb);

insert into public.addresses (
  customer_id, label, address_text, city, area, street, building_number,
  recipient_name, recipient_phone, latitude, longitude
) values
  ('00000000-0000-0000-0000-000000000001', 'Home', 'Customer one address', 'KHARTOUM', 'Area 1', 'Street 1', '1', 'Customer One', '+249900000001', 15.5900, 32.5600),
  ('00000000-0000-0000-0000-000000000002', 'Home', 'Customer two address', 'KHARTOUM', 'Area 2', 'Street 2', '2', 'Customer Two', '+249900000002', 15.5910, 32.5610);

update public.profiles
   set role = 'merchant'::public.wasil_role
 where id = '00000000-0000-0000-0000-000000000002';
update public.profiles
   set role = 'driver'::public.wasil_role
 where id = '00000000-0000-0000-0000-000000000003';

insert into public.stores (
  id, owner_id, category_id, name_ar, name_en, phone, address_text,
  latitude, longitude, delivery_fee, min_order_amount, is_active, is_open
) values (
  '10000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  (select id from public.categories where slug = 'restaurants'),
  'متجر اختبار', 'Smoke Test Store', '+249900000002', 'Test store address',
  15.5898, 32.5599, 4.50, 15.00, true, true
);

insert into public.products (
  id, store_id, name_ar, price, stock_quantity, is_available, is_active
) values (
  '20000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000001',
  'منتج اختبار', 10.00, 3, true, true
);

insert into public.driver_profiles (
  profile_id, vehicle_type, is_available, latitude, longitude, last_location_at
) values (
  '00000000-0000-0000-0000-000000000003', 'motorcycle', true, 15.5900, 32.5600, now()
);

update public.platform_settings set auto_assign_drivers = true, commission_rate_percent = 10 where id = true;

begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
do $$
declare
  v_order_id uuid;
  v_retry_order_id uuid;
  v_second_address_id uuid;
  v_far_address_id uuid;
  v_stock integer;
  v_total numeric(12,2);
  v_distance numeric(8,2);
  v_radius_rejected boolean := false;
  v_minimum_rejected boolean := false;
  v_offline_rejected boolean := false;
begin
  v_order_id := public.wasil_create_order(
    '10000000-0000-0000-0000-000000000001',
    (select id from public.addresses where customer_id = '00000000-0000-0000-0000-000000000001' and label = 'Home' order by created_at limit 1),
    jsonb_build_array(jsonb_build_object(
      'product_id', '20000000-0000-0000-0000-000000000001',
      'quantity', 2,
      'special_instructions', 'smoke test'
    )),
    '30000000-0000-0000-0000-000000000001',
    'Order RPC smoke test'
  );

  v_retry_order_id := public.wasil_create_order(
    '10000000-0000-0000-0000-000000000001',
    (select id from public.addresses where customer_id = '00000000-0000-0000-0000-000000000001' and label = 'Home' order by created_at limit 1),
    jsonb_build_array(jsonb_build_object(
      'product_id', '20000000-0000-0000-0000-000000000001',
      'quantity', 2,
      'special_instructions', 'smoke test'
    )),
    '30000000-0000-0000-0000-000000000001',
    'Order RPC retry smoke test'
  );

  if v_order_id <> v_retry_order_id then
    raise exception 'Idempotent order retry returned a different order';
  end if;

  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 1 then
    raise exception 'Idempotent order creation decremented stock incorrectly: %', v_stock;
  end if;

  select total into v_total from public.orders where id = v_order_id;
  if v_total <> 24.50 then
    raise exception 'Server-calculated order total is wrong: expected 24.50, got %', v_total;
  end if;

  if (select platform_commission_amount from public.orders where id = v_order_id) <> 2.00 then
    raise exception 'Configured 10%% platform commission should be 2.00 on a 20.00 subtotal';
  end if;

  select distance_km into v_distance from public.orders where id = v_order_id;
  if v_distance <= 0 then
    raise exception 'Server did not calculate delivery distance from available coordinates';
  end if;

  insert into public.addresses (
    customer_id, label, address_text, city, area, street, building_number,
    recipient_name, recipient_phone, latitude, longitude
  ) values (
    '00000000-0000-0000-0000-000000000001', 'Far', 'Far-away address',
    'KHARTOUM', 'Far area', 'Far street', '9', 'Customer One', '+249900000001',
    0.0, 0.0
  ) returning id into v_far_address_id;

  update public.platform_settings set max_delivery_radius_km = 1 where id = true;
  begin
    perform public.wasil_create_order(
      '10000000-0000-0000-0000-000000000001',
      v_far_address_id,
      jsonb_build_array(jsonb_build_object(
        'product_id', '20000000-0000-0000-0000-000000000001',
        'quantity', 1,
        'special_instructions', ''
      )),
      '30000000-0000-0000-0000-000000000004',
      'Out-of-radius smoke test'
    );
  exception when sqlstate '22023' then
    v_radius_rejected := true;
  end;
  if not v_radius_rejected then
    raise exception 'Order outside configured delivery radius was not rejected';
  end if;
  update public.platform_settings set max_delivery_radius_km = 25 where id = true;
  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 1 then
    raise exception 'Rejected out-of-radius order changed product stock';
  end if;

  update public.stores set min_order_amount = 25.00
  where id = '10000000-0000-0000-0000-000000000001';
  begin
    perform public.wasil_create_order(
      '10000000-0000-0000-0000-000000000001',
      (select id from public.addresses where customer_id = '00000000-0000-0000-0000-000000000001' and label = 'Home' order by created_at limit 1),
      jsonb_build_array(jsonb_build_object(
        'product_id', '20000000-0000-0000-0000-000000000001',
        'quantity', 1,
        'special_instructions', ''
      )),
      '30000000-0000-0000-0000-000000000002',
      'Below minimum smoke test'
    );
  exception when sqlstate '22023' then
    v_minimum_rejected := true;
  end;
  if not v_minimum_rejected then
    raise exception 'Order below store minimum was not rejected';
  end if;

  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 1 then
    raise exception 'Rejected below-minimum order changed stock';
  end if;

  update public.orders set status = 'ready_for_pickup'
  where id = v_order_id;
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
  if not public.wasil_auto_assign_ready_order(v_order_id) then
    raise exception 'Enabled auto-assignment did not assign the eligible nearby driver';
  end if;
  if not exists (
    select 1 from public.orders
    where id = v_order_id
      and driver_id = '00000000-0000-0000-0000-000000000003'
      and status = 'driver_assigned'
      and assigned_at is not null
  ) then
    raise exception 'Auto-assignment did not persist the selected driver and assignment timestamp';
  end if;
  if (select is_available from public.driver_profiles
      where profile_id = '00000000-0000-0000-0000-000000000003') then
    raise exception 'Auto-assigned driver must become unavailable while handling the order';
  end if;

  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
  begin
    perform public.wasil_set_driver_availability(false);
  exception when sqlstate '22023' then
    v_offline_rejected := true;
  end;
  if not v_offline_rejected then
    raise exception 'Driver was allowed to go offline during an active delivery';
  end if;

  perform public.wasil_driver_update_location(15.5910, 32.5610);
  if not exists (
    select 1 from public.driver_profiles
    where profile_id = '00000000-0000-0000-0000-000000000003'
      and latitude = 15.5910
      and longitude = 32.5610
      and last_location_at > now() - interval '5 seconds'
  ) then
    raise exception 'Driver location RPC did not store coordinates with a server timestamp';
  end if;

  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

  insert into public.addresses (
    customer_id, label, address_text, city, area, street, building_number,
    recipient_name, recipient_phone
  ) values (
    '00000000-0000-0000-0000-000000000001', 'Work', 'Second customer address',
    'KHARTOUM', 'Area 3', 'Street 3', '3', 'Customer One', '+249900000001'
  ) returning id into v_second_address_id;

  perform public.wasil_set_default_address(v_second_address_id);

  if not (select is_default from public.addresses where id = v_second_address_id) then
    raise exception 'Default-address RPC did not mark the selected address';
  end if;
  if (select count(*) from public.addresses
      where customer_id = '00000000-0000-0000-0000-000000000001' and is_default) <> 1 then
    raise exception 'Customer must have exactly one default address after RPC';
  end if;
end;
$$;
commit;

-- Cancellation smoke: stock is restored once, and a repeated attempt cannot restore it again.
begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
update public.stores
   set min_order_amount = 0
 where id = '10000000-0000-0000-0000-000000000001';

do $wasil_cancel_smoke$
declare
  v_order_id uuid;
  v_stock integer;
  v_second_cancel_rejected boolean := false;
begin
  v_order_id := public.wasil_create_order(
    '10000000-0000-0000-0000-000000000001',
    (select id from public.addresses
      where customer_id = '00000000-0000-0000-0000-000000000001'
      order by created_at limit 1),
    jsonb_build_array(jsonb_build_object(
      'product_id', '20000000-0000-0000-0000-000000000001',
      'quantity', 1,
      'special_instructions', ''
    )),
    '30000000-0000-0000-0000-000000000003',
    'Cancellation stock restoration smoke test'
  );

  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 0 then
    raise exception 'Order creation did not reserve the last stock unit';
  end if;

  if not public.wasil_cancel_order(v_order_id, 'Smoke-test cancellation') then
    raise exception 'Customer cancellation returned false for a cancellable order';
  end if;

  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 1 then
    raise exception 'Cancellation did not restore the reserved stock unit exactly once';
  end if;

  begin
    perform public.wasil_cancel_order(v_order_id, 'Repeated smoke-test cancellation');
  exception when sqlstate '42501' or sqlstate '22023' then
    v_second_cancel_rejected := true;
  end;
  if not v_second_cancel_rejected then
    raise exception 'Repeated cancellation should be rejected after the order is cancelled';
  end if;

  select stock_quantity into v_stock
  from public.products where id = '20000000-0000-0000-0000-000000000001';
  if v_stock <> 1 then
    raise exception 'Repeated cancellation changed stock a second time';
  end if;
end;
$wasil_cancel_smoke$;
commit;

begin;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

do $$
declare
  v_visible_addresses integer;
begin
  select count(*) into v_visible_addresses from public.addresses;
  if v_visible_addresses <> 3 then
    raise exception 'RLS leak: customer can see % addresses; expected their three owned addresses only', v_visible_addresses;
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
