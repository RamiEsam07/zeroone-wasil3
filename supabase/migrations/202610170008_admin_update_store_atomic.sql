-- Atomic administrator store edits: metadata + open/approved state in one transaction.
-- Draft only: validate with disposable PostgreSQL/Supabase before applying to production.

begin;

create or replace function public.wasil_admin_update_store(
  p_store_id uuid,
  p_name_en text,
  p_name_ar text,
  p_phone text,
  p_address_text text,
  p_is_open boolean,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $wasil_admin_update_store$
declare
  v_owner_id uuid;
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() or not public.is_wasil_admin() then
    raise exception 'Active administrator account required' using errcode = '42501';
  end if;

  if p_store_id is null
     or nullif(trim(p_name_en), '') is null
     or nullif(trim(p_name_ar), '') is null
     or p_phone is null
     or p_address_text is null
     or p_is_open is null
     or p_is_active is null then
    raise exception 'Store fields are required' using errcode = '22023';
  end if;

  select s.owner_id
    into v_owner_id
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
     set name_en = trim(p_name_en),
         name_ar = trim(p_name_ar),
         phone = trim(p_phone),
         address_text = trim(p_address_text),
         is_open = p_is_open,
         is_active = p_is_active,
         updated_at = now()
   where id = p_store_id;

  return true;
end;
$wasil_admin_update_store$;

revoke all on function public.wasil_admin_update_store(uuid, text, text, text, text, boolean, boolean)
  from public, anon;
grant execute on function public.wasil_admin_update_store(uuid, text, text, text, text, boolean, boolean)
  to authenticated;

commit;
