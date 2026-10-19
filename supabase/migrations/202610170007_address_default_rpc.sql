-- WASIL atomic default-address management and least-privilege address columns.
-- Validate against a disposable Supabase project before applying.

begin;

revoke insert, update on public.addresses from authenticated;
grant insert (
  customer_id, label, address_text, city, area, street, building_number,
  floor_or_apartment, landmark, recipient_name, recipient_phone,
  latitude, longitude, details
) on public.addresses to authenticated;
grant update (
  label, address_text, city, area, street, building_number,
  floor_or_apartment, landmark, recipient_name, recipient_phone,
  latitude, longitude, details
) on public.addresses to authenticated;

create or replace function public.wasil_set_default_address(p_address_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.current_wasil_account_active() then
    raise exception 'Account is inactive' using errcode = '42501';
  end if;

  if public.current_wasil_role() is distinct from 'customer'::public.wasil_role then
    raise exception 'Only customer accounts can change their default address' using errcode = '42501';
  end if;

  if p_address_id is null then
    raise exception 'Address is required' using errcode = '22023';
  end if;

  -- Lock the customer profile so concurrent requests cannot create two defaults.
  perform 1 from public.profiles p where p.id = v_uid for update;

  if not exists (
    select 1 from public.addresses a
    where a.id = p_address_id and a.customer_id = v_uid
  ) then
    raise exception 'Address not found for this customer' using errcode = '42501';
  end if;

  update public.addresses
     set is_default = false
   where customer_id = v_uid and is_default;

  update public.addresses
     set is_default = true
   where id = p_address_id and customer_id = v_uid;

  return true;
end;
$$;

revoke all on function public.wasil_set_default_address(uuid) from public, anon;
grant execute on function public.wasil_set_default_address(uuid) to authenticated;

commit;
