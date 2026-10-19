-- Runtime state for the Supabase Edge Function based Razorpay subscription flow.
-- Google account identifiers are stored as opaque provider subjects; financial app
-- data never leaves the device. All writes use a Supabase secret key from the Edge
-- Function. Mobile roles receive no direct table access.

create table if not exists public.billing_attempts (
  id uuid primary key default gen_random_uuid(),
  account_id text not null check (account_id ~ '^google:[0-9]+$'),
  request_key text not null check (char_length(request_key) between 16 and 80),
  plan_id text not null references public.billing_plans(id),
  razorpay_plan_id text not null check (razorpay_plan_id ~ '^plan_[A-Za-z0-9]+$'),
  amount_paise integer not null check (amount_paise > 0),
  currency text not null default 'INR' check (currency = 'INR'),
  provider_subscription_id text unique check (
    provider_subscription_id is null or provider_subscription_id ~ '^sub_[A-Za-z0-9]+$'
  ),
  status text not null check (status in (
    'creating', 'created', 'authenticated', 'active', 'pending', 'halted',
    'paused', 'cancelled', 'completed', 'expired', 'rejected'
  )),
  cancel_requested boolean not null default false,
  created_at timestamptz not null default now(),
  last_checked_at timestamptz,
  unique (account_id, request_key)
);

create unique index if not exists billing_attempts_one_open_mandate_idx
  on public.billing_attempts (account_id)
  where status not in ('cancelled', 'completed', 'expired', 'rejected');
create index if not exists billing_attempts_account_created_idx
  on public.billing_attempts (account_id, created_at desc);

alter table public.billing_entitlements
  alter column user_id drop not null;
alter table public.billing_entitlements
  add column if not exists account_id text check (account_id is null or account_id ~ '^google:[0-9]+$'),
  add column if not exists attempt_id uuid references public.billing_attempts(id),
  add column if not exists provider_invoice_id text,
  add column if not exists amount_paise integer check (amount_paise is null or amount_paise > 0),
  add column if not exists currency text check (currency is null or currency = 'INR');

alter table public.billing_entitlements
  drop constraint if exists billing_entitlements_owner_check;
alter table public.billing_entitlements
  add constraint billing_entitlements_owner_check check (
    (user_id is not null and account_id is null)
    or (user_id is null and account_id is not null)
  );
create unique index if not exists billing_entitlements_provider_invoice_idx
  on public.billing_entitlements (provider, provider_invoice_id)
  where provider_invoice_id is not null;
create index if not exists billing_entitlements_account_expiry_idx
  on public.billing_entitlements (account_id, expires_at desc)
  where revoked_at is null and account_id is not null;

alter table public.billing_webhook_events
  add column if not exists event_type text,
  add column if not exists provider_subscription_id text,
  add column if not exists provider_payment_id text,
  add column if not exists processing_error text;

alter table public.billing_attempts enable row level security;
revoke all on public.billing_attempts from anon, authenticated;
revoke all on public.billing_entitlements, public.billing_webhook_events from anon, authenticated;
grant select on public.billing_plans to authenticated;

