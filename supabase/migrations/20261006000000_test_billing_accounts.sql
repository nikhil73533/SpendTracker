-- Test-only billing sessions use server-signed UUID account identifiers. The
-- Edge Function accepts them only while Razorpay is configured with rzp_test_.
alter table public.billing_attempts
  drop constraint if exists billing_attempts_account_id_check;
alter table public.billing_attempts
  add constraint billing_attempts_account_id_check check (
    account_id ~ '^google:[0-9]+$' or account_id ~ '^test:[0-9a-f-]{36}$'
  );

alter table public.billing_entitlements
  drop constraint if exists billing_entitlements_account_id_check;
alter table public.billing_entitlements
  add constraint billing_entitlements_account_id_check check (
    account_id is null or account_id ~ '^google:[0-9]+$' or account_id ~ '^test:[0-9a-f-]{36}$'
  );
