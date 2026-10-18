-- WASIL trusted admin provisioning and store approval (draft; validate before applying)
-- The first administrator must be bootstrapped through the Supabase SQL Editor or another
-- trusted service-role workflow. No client can grant itself elevated roles.

begin;

create or replace function public.wasil_admin_set_user_access(
  p_user_id uuid,
  p_role public.wasil_role,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_current_role public.wasil_role;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;

  if p_user_id is null or p_role is null or p_is_active is null then
    raise exception 'User, role, and active state are required' using errcode = '22023';
  end if;

  if p_user_id = v_uid then
    raise exception 'Administrators cannot change their own access through this RPC' using errcode = '42501';
  end if;

  select p.role into v_current_role
  from public.profiles p
  where p.id = p_user_id;

  if not found then
    raise exception 'Target profile not found' using errcode = '22023';
  end if;

  if exists (
    select 1
    from public.orders o
    where (
      o.customer_id = p_user_id
      or o.driver_id = p_user_id
      or exists (
        select 1 from public.stores s
        where s.id = o.store_id and s.owner_id = p_user_id
      )
    )
      and o.status not in ('delivered', 'cancelled')
  ) and (
    not p_is_active
    or (v_current_role = 'driver'::public.wasil_role and p_role <> 'driver'::public.wasil_role)
    or (v_current_role = 'merchant'::public.wasil_role and p_role <> 'merchant'::public.wasil_role)
  ) then
    raise exception 'Cannot disable this account or change its role while it has active orders'
      using errcode = '22023';
  end if;

  if v_current_role = 'admin'::public.wasil_role
     and (p_role <> 'admin'::public.wasil_role or not p_is_active)
     and (
       select count(*) from public.profiles p
       where p.role = 'admin'::public.wasil_role and p.is_active
     ) <= 1 then
    raise exception 'Cannot remove the last active administrator'
      using errcode = '22023';
  end if;

  update public.profiles
     set role = p_role,
         is_active = p_is_active,
         updated_at = now()
   where id = p_user_id;

  if p_role = 'driver'::public.wasil_role then
    insert into public.driver_profiles (profile_id)
    values (p_user_id)
    on conflict (profile_id) do nothing;
  end if;

  return true;
end;
$$;

revoke all on function public.wasil_admin_set_user_access(uuid, public.wasil_role, boolean) from public, anon;
grant execute on function public.wasil_admin_set_user_access(uuid, public.wasil_role, boolean) to authenticated;

create or replace function public.wasil_admin_set_store_active(
  p_store_id uuid,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_owner_id uuid;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;

  if p_store_id is null or p_is_active is null then
    raise exception 'Store and active state are required' using errcode = '22023';
  end if;

  select s.owner_id into v_owner_id
  from public.stores s
  where s.id = p_store_id
  for update;

  if not found then
    return false;
  end if;

  if p_is_active and not exists (
    select 1
    from public.profiles p
    where p.id = v_owner_id
      and p.role = 'merchant'::public.wasil_role
      and p.is_active
  ) then
    raise exception 'Store owner must be an active merchant before store approval'
      using errcode = '22023';
  end if;

  if not p_is_active and exists (
    select 1
    from public.orders o
    where o.store_id = p_store_id
      and o.status not in ('delivered', 'cancelled')
  ) then
    raise exception 'Cannot deactivate a store while it has active orders'
      using errcode = '22023';
  end if;

  update public.stores
     set is_active = p_is_active,
         updated_at = now()
   where id = p_store_id;

  return true;
end;
$$;

revoke all on function public.wasil_admin_set_store_active(uuid, boolean) from public, anon;
grant execute on function public.wasil_admin_set_store_active(uuid, boolean) to authenticated;

create or replace function public.wasil_admin_assign_driver(
  p_order_id uuid,
  p_driver_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $admin_assign_driver$
declare
  v_uid uuid := auth.uid();
  v_status public.wasil_order_status;
  v_assigned_driver uuid;
  v_available boolean;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;
  if p_order_id is null or p_driver_id is null then
    raise exception 'Order and driver are required' using errcode = '22023';
  end if;

  select o.status, o.driver_id into v_status, v_assigned_driver
  from public.orders o where o.id = p_order_id for update;
  if not found or v_status <> 'ready_for_pickup' or v_assigned_driver is not null then
    return false;
  end if;

  select d.is_available into v_available
  from public.driver_profiles d
  join public.profiles p on p.id = d.profile_id
  where d.profile_id = p_driver_id
    and p.role = 'driver'::public.wasil_role
    and p.is_active
  for update of d;
  if not found or not coalesce(v_available, false) then
    return false;
  end if;

  if exists (
    select 1 from public.orders o
    where o.driver_id = p_driver_id
      and o.status in ('driver_assigned', 'picked_up', 'on_the_way')
  ) then
    return false;
  end if;

  update public.orders
     set driver_id = p_driver_id, status = 'driver_assigned',
         assigned_at = now(), updated_at = now()
   where id = p_order_id and driver_id is null and status = 'ready_for_pickup';
  if not found then
    return false;
  end if;

  update public.driver_profiles
     set is_available = false, updated_at = now()
   where profile_id = p_driver_id;
  return true;
end;
$admin_assign_driver$;

revoke all on function public.wasil_admin_assign_driver(uuid, uuid) from public, anon;
grant execute on function public.wasil_admin_assign_driver(uuid, uuid) to authenticated;

create or replace function public.wasil_admin_set_driver_availability(
  p_driver_id uuid,
  p_is_available boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $admin_driver_availability$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;
  if p_driver_id is null or p_is_available is null then
    raise exception 'Driver and availability are required' using errcode = '22023';
  end if;
  if not exists (
    select 1 from public.driver_profiles d
    join public.profiles p on p.id = d.profile_id
    where d.profile_id = p_driver_id
      and p.role = 'driver'::public.wasil_role
      and p.is_active
  ) then
    raise exception 'Active driver profile not found' using errcode = '22023';
  end if;
  if p_is_available and exists (
    select 1 from public.orders o
    where o.driver_id = p_driver_id
      and o.status in ('driver_assigned', 'picked_up', 'on_the_way')
  ) then
    raise exception 'Driver has an active delivery and cannot be marked available'
      using errcode = '22023';
  end if;
  update public.driver_profiles
     set is_available = p_is_available, updated_at = now()
   where profile_id = p_driver_id;
  return true;
end;
$admin_driver_availability$;

revoke all on function public.wasil_admin_set_driver_availability(uuid, boolean) from public, anon;
grant execute on function public.wasil_admin_set_driver_availability(uuid, boolean) to authenticated;

create or replace function public.wasil_admin_upsert_category(
  p_category_id uuid,
  p_name_ar text,
  p_name_en text,
  p_slug text,
  p_sort_order integer
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $admin_upsert_category$
declare
  v_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;
  if nullif(trim(p_name_ar), '') is null or nullif(trim(p_name_en), '') is null
     or nullif(trim(p_slug), '') is null or p_sort_order < 0 then
    raise exception 'Category names, slug and non-negative sort order are required' using errcode = '22023';
  end if;

  if p_category_id is null then
    insert into public.categories (name_ar, name_en, slug, sort_order, is_active)
    values (trim(p_name_ar), trim(p_name_en), lower(trim(p_slug)), p_sort_order, true)
    returning id into v_id;
  else
    update public.categories
       set name_ar = trim(p_name_ar), name_en = trim(p_name_en),
           slug = lower(trim(p_slug)), sort_order = p_sort_order, is_active = true
     where id = p_category_id
     returning id into v_id;
  end if;
  if v_id is null then return null; end if;
  return v_id;
end;
$admin_upsert_category$;

revoke all on function public.wasil_admin_upsert_category(uuid, text, text, text, integer) from public, anon;
grant execute on function public.wasil_admin_upsert_category(uuid, text, text, text, integer) to authenticated;

create or replace function public.wasil_admin_set_category_active(
  p_category_id uuid,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $admin_set_category_active$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;
  if p_category_id is null or p_is_active is null then
    raise exception 'Category and active state are required' using errcode = '22023';
  end if;
  if not p_is_active and exists (
    select 1 from public.stores s where s.category_id = p_category_id
  ) then
    raise exception 'Cannot deactivate a category that is assigned to a store'
      using errcode = '22023';
  end if;
  update public.categories set is_active = p_is_active where id = p_category_id;
  return found;
end;
$admin_set_category_active$;

revoke all on function public.wasil_admin_set_category_active(uuid, boolean) from public, anon;
grant execute on function public.wasil_admin_set_category_active(uuid, boolean) to authenticated;

commit;

-- Bootstrap exactly one trusted administrator out-of-band using the real auth.users UUID:
-- update public.profiles set role = 'admin', is_active = true where id = '<auth-user-uuid>';
-- Never expose this bootstrap statement or a service-role key in the Android application.
