-- Billing administration is performed by billing-admin with a server-side secret
-- key. Remove the earlier direct client mutation/audit policies and keep the Data
-- API surface read-only for active catalog rows and each Supabase user's own legacy
-- entitlement rows.
drop policy if exists "admins manage plan records" on public.billing_plans;
drop policy if exists "admins view billing audit records" on public.billing_entitlements;
drop policy if exists "admins view webhook audit records" on public.billing_webhook_events;
drop policy if exists "authenticated users read published billing plans" on public.billing_plans;
create policy "authenticated users read published billing plans"
  on public.billing_plans for select to authenticated
  using (active);

create index if not exists billing_admins_granted_by_idx
  on public.billing_admins (granted_by) where granted_by is not null;
create index if not exists billing_attempts_plan_id_idx
  on public.billing_attempts (plan_id);
create index if not exists billing_entitlements_attempt_id_idx
  on public.billing_entitlements (attempt_id) where attempt_id is not null;
create index if not exists billing_plans_updated_by_idx
  on public.billing_plans (updated_by) where updated_by is not null;

