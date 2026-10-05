-- Premium billing control plane. Apply through the Supabase CLI or Dashboard,
-- never from the Android client. Amounts are integer paise.
create table if not exists public.billing_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  granted_at timestamptz not null default now(),
  granted_by uuid references auth.users(id)
);

create table if not exists public.billing_plans (
  id text primary key check (id in ('monthly', 'quarterly', 'six_monthly', 'yearly')),
  title text not null,
  amount_paise integer not null check (amount_paise > 0),
  currency text not null default 'INR' check (currency = 'INR'),
  razorpay_plan_id text not null unique check (razorpay_plan_id ~ '^plan_[A-Za-z0-9]+$'),
  period text not null check (period in ('monthly', 'yearly')),
  interval integer not null check (interval > 0),
  total_count integer not null check (total_count > 0),
  active boolean not null default true,
  catalog_version integer not null default 1 check (catalog_version > 0),
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id)
);

create table if not exists public.billing_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text not null check (provider in ('razorpay', 'google_play')),
  provider_subscription_id text,
  provider_payment_id text not null,
  starts_at timestamptz not null,
  expires_at timestamptz not null check (expires_at > starts_at),
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  unique (provider, provider_payment_id)
);

create table if not exists public.billing_webhook_events (
  provider text not null default 'razorpay',
  provider_event_id text not null,
  body_sha256 text not null check (body_sha256 ~ '^[a-f0-9]{64}$'),
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  primary key (provider, provider_event_id)
);

create index if not exists billing_entitlements_active_user_idx
  on public.billing_entitlements (user_id, expires_at desc)
  where revoked_at is null;

alter table public.billing_admins enable row level security;
alter table public.billing_plans enable row level security;
alter table public.billing_entitlements enable row level security;
alter table public.billing_webhook_events enable row level security;

create policy "users can read only their own admin grant"
  on public.billing_admins for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "users read their own active entitlement history"
  on public.billing_entitlements for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "authenticated users read published billing plans"
  on public.billing_plans for select to authenticated
  using (active);

create policy "admins manage plan records"
  on public.billing_plans for all to authenticated
  using (exists (select 1 from public.billing_admins a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.billing_admins a where a.user_id = (select auth.uid())));

create policy "admins view billing audit records"
  on public.billing_entitlements for select to authenticated
  using (exists (select 1 from public.billing_admins a where a.user_id = (select auth.uid())));
create policy "admins view webhook audit records"
  on public.billing_webhook_events for select to authenticated
  using (exists (select 1 from public.billing_admins a where a.user_id = (select auth.uid())));

-- No INSERT/UPDATE/DELETE policy exists for entitlements or webhooks. Only the
-- trusted webhook/checkout worker may write them using a Supabase secret key.
revoke all on public.billing_admins, public.billing_plans,
  public.billing_entitlements, public.billing_webhook_events from anon, authenticated;
grant select on public.billing_plans, public.billing_entitlements to authenticated;
