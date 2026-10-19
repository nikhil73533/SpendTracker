-- Add fixed-term, non-renewing Razorpay Orders alongside the existing
-- Razorpay Subscription/UPI AutoPay flow. Provider secrets and all writes
-- remain server-only.

alter table public.billing_attempts
  add column if not exists purchase_type text not null default 'subscription',
  add column if not exists provider_order_id text,
  add column if not exists provider_payment_id text,
  add column if not exists access_months integer;

alter table public.billing_attempts
  alter column razorpay_plan_id drop not null;

alter table public.billing_attempts
  drop constraint if exists billing_attempts_status_check;
alter table public.billing_attempts
  add constraint billing_attempts_status_check check (status in (
    'creating', 'created', 'attempted', 'paid', 'refunded', 'failed',
    'authenticated', 'active', 'pending', 'halted', 'paused',
    'cancelled', 'completed', 'expired', 'rejected'
  ));

alter table public.billing_attempts
  add constraint billing_attempts_purchase_type_check
    check (purchase_type in ('subscription', 'one_time')),
  add constraint billing_attempts_provider_order_id_check
    check (provider_order_id is null or provider_order_id ~ '^order_[A-Za-z0-9]+$'),
  add constraint billing_attempts_provider_payment_id_check
    check (provider_payment_id is null or provider_payment_id ~ '^pay_[A-Za-z0-9]+$'),
  add constraint billing_attempts_access_months_check
    check (access_months is null or access_months in (1, 3, 6, 12)),
  add constraint billing_attempts_purchase_shape_check check (
    (purchase_type = 'subscription' and razorpay_plan_id is not null and access_months is null and provider_order_id is null)
    or
    (purchase_type = 'one_time' and razorpay_plan_id is null and access_months is not null and provider_subscription_id is null)
  );

create unique index if not exists billing_attempts_provider_order_idx
  on public.billing_attempts (provider_order_id)
  where provider_order_id is not null;
create unique index if not exists billing_attempts_provider_payment_idx
  on public.billing_attempts (provider_payment_id)
  where provider_payment_id is not null;

drop index if exists public.billing_attempts_one_open_mandate_idx;
create unique index billing_attempts_one_open_mandate_idx
  on public.billing_attempts (account_id)
  where purchase_type = 'subscription'
    and status not in ('cancelled', 'completed', 'expired', 'rejected');

alter table public.billing_webhook_events
  add column if not exists provider_order_id text;
