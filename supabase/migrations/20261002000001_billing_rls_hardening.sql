-- Remove the initial privileged policy helper. Admin privileges are now derived
-- only from the caller-owned row and are never exposed as a privileged RPC.
drop policy if exists "authenticated users read published billing plans" on public.billing_plans;
drop policy if exists "admins manage plan records" on public.billing_plans;
drop policy if exists "admins view billing audit records" on public.billing_entitlements;
drop policy if exists "admins view webhook audit records" on public.billing_webhook_events;
drop policy if exists "users can read only their own admin grant" on public.billing_admins;
drop function if exists public.is_billing_admin();

create policy "users can read only their own admin grant"
  on public.billing_admins for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "authenticated users read published billing plans"
  on public.billing_plans for select to authenticated
  using (active);

-- Catalog publication and audit access go through trusted server-side code.
-- Client roles can read only active plans and their own entitlement rows.
revoke all on public.billing_admins, public.billing_plans,
  public.billing_entitlements, public.billing_webhook_events from anon, authenticated;
grant select on public.billing_plans, public.billing_entitlements to authenticated;
