// TrackMint Razorpay subscriptions on Supabase Edge Functions.
// Deploy with verify_jwt=false: user routes validate Google ID tokens explicitly,
// while the webhook route validates Razorpay's HMAC over the untouched request body.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.4";
import { hasCompleteCatalog } from "./catalog-validation.ts";

type Json = Record<string, any>;
type Attempt = {
  id: string; account_id: string; request_key: string; plan_id: string;
  purchase_type: "subscription" | "one_time"; razorpay_plan_id: string | null;
  amount_paise: number; currency: string; access_months: number | null;
  provider_subscription_id: string | null; provider_order_id: string | null;
  provider_payment_id: string | null; status: string;
  cancel_requested: boolean; created_at: string;
};

class HttpError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}

const TERMINAL = new Set(["cancelled", "completed", "expired", "rejected"]);
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  },
});
const required = (name: string) => {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new HttpError(503, `Billing configuration is missing: ${name}`);
  return value;
};
const configured = (name: string) => Boolean(Deno.env.get(name)?.trim());
const razorpayKeyId = () => required("RAZORPAY_KEY_ID");
const razorpayKeySecret = () => required("RAZORPAY_KEY_SECRET");
const isTestMode = () => razorpayKeyId().startsWith("rzp_test_");
const checkoutEnabled = () => Deno.env.get("BILLING_CHECKOUT_ENABLED") === "true" || isTestMode();
const platformKey = (currentName: string, legacyName: string) => {
  const current = Deno.env.get(currentName);
  if (current) {
    try { return JSON.parse(current).default as string; } catch (_) { /* legacy fallback */ }
  }
  return Deno.env.get(legacyName) || "";
};
const bytesToHex = (bytes: ArrayBuffer) => [...new Uint8Array(bytes)]
  .map((value) => value.toString(16).padStart(2, "0")).join("");
const constantTimeEqual = (left: string, right: string) => {
  if (left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index++) mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return mismatch === 0;
};
const hmac = async (secret: string, value: string) => {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return bytesToHex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value)));
};
const base64Url = (value: string) => btoa(value)
  .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
const fromBase64Url = (value: string) => atob(value.replaceAll("-", "+").replaceAll("_", "/")
  + "=".repeat((4 - value.length % 4) % 4));
const digest = async (value: string) => bytesToHex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
const providerId = (value: unknown, prefix: string) => {
  if (typeof value !== "string" || !new RegExp(`^${prefix}_[A-Za-z0-9]+$`).test(value)) {
    throw new HttpError(400, "Invalid payment provider identifier");
  }
  return value;
};

const supabaseUrl = required("SUPABASE_URL");
const secretKey = platformKey("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY");
if (!secretKey) throw new Error("Supabase secret key is unavailable");
const db = createClient(supabaseUrl, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });

const razorpay = async (method: string, path: string, body?: Json): Promise<Json> => {
  const keyId = razorpayKeyId();
  const keySecret = razorpayKeySecret();
  let response: Response;
  try {
    response = await fetch(`https://api.razorpay.com/v1/${path}`, {
      method,
      headers: {
        Authorization: `Basic ${btoa(`${keyId}:${keySecret}`)}`,
        "Content-Type": "application/json",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(20_000),
    });
  } catch (_) {
    throw new HttpError(503, "Payment provider unavailable; check status before retrying");
  }
  let data: Json;
  try { data = await response.json(); } catch (_) { throw new HttpError(503, "Invalid payment provider response"); }
  if (!response.ok) throw new HttpError(503, "Payment provider unavailable; check status before retrying");
  return data;
};

const razorpayItems = async (path: string) => {
  const all: Json[] = [];
  for (let skip = 0; skip < 10_000; skip += 100) {
    const join = path.includes("?") ? "&" : "?";
    const page = await razorpay("GET", `${path}${join}count=100&skip=${skip}`);
    if (!Array.isArray(page.items)) throw new HttpError(503, "Invalid payment provider response");
    all.push(...page.items);
    if (page.items.length < 100) return all;
  }
  throw new HttpError(503, "Payment provider pagination requires support review");
};

const authenticate = async (request: Request) => {
  const authorization = request.headers.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ") || authorization.length > 8192) {
    throw new HttpError(401, "Sign in required");
  }
  const token = authorization.slice(7);
  if (token.startsWith("tm_test_")) {
    if (!isTestMode()) throw new HttpError(401, "Test billing is unavailable");
    const parts = token.slice(8).split(".");
    if (parts.length !== 2 || !constantTimeEqual(await hmac(razorpayKeySecret(), parts[0]), parts[1])) {
      throw new HttpError(401, "Test billing session is invalid");
    }
    let claims: Json;
    try { claims = JSON.parse(fromBase64Url(parts[0])); } catch (_) { throw new HttpError(401, "Test billing session is invalid"); }
    if (typeof claims.account !== "string" || !/^test:[0-9a-f-]{36}$/.test(claims.account)
        || !Number.isInteger(claims.exp) || claims.exp <= Math.floor(Date.now() / 1000)) {
      throw new HttpError(401, "Test billing session has expired");
    }
    return claims.account;
  }
  const response = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(token)}`, {
    signal: AbortSignal.timeout(10_000),
  }).catch(() => null);
  if (!response?.ok) throw new HttpError(401, "Sign in again to manage your subscription");
  const claims = await response.json();
  const issuer = claims.iss;
  if (claims.aud !== required("GOOGLE_WEB_CLIENT_ID") || !claims.sub
      || (issuer !== "accounts.google.com" && issuer !== "https://accounts.google.com")
      || ![true, "true"].includes(claims.email_verified)) {
    throw new HttpError(401, "Sign in again to manage your subscription");
  }
  return `google:${claims.sub}`;
};

const loadAttempt = async (account: string, id: string): Promise<Attempt> => {
  const { data, error } = await db.from("billing_attempts").select("*")
    .eq("id", id).eq("account_id", account).maybeSingle();
  if (error) throw new HttpError(500, "Unable to load subscription");
  if (!data) throw new HttpError(404, "Subscription not found");
  return data as Attempt;
};

const attemptView = (attempt: Attempt) => ({
  id: attempt.id,
  plan_id: attempt.plan_id,
  amount: attempt.amount_paise,
  currency: attempt.currency,
  subscription_id: attempt.provider_subscription_id,
  order_id: attempt.provider_order_id,
  purchase_type: attempt.purchase_type,
  status: attempt.status,
  cancel_requested: attempt.cancel_requested,
  // The key ID is public and is required by Razorpay Checkout. The secret is
  // never returned to Android and remains in Supabase Edge Function Secrets.
  key_id: razorpayKeyId(),
  checkout_allowed: checkoutEnabled() && !attempt.cancel_requested
    && (attempt.purchase_type === "subscription"
      ? attempt.status === "created" : ["created", "attempted"].includes(attempt.status)),
});

const oneTimeExpiry = (startSeconds: number, months: number) => {
  const start = new Date(startSeconds * 1000);
  const day = start.getUTCDate();
  start.setUTCDate(1);
  start.setUTCMonth(start.getUTCMonth() + months);
  const finalDay = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 0)).getUTCDate();
  start.setUTCDate(Math.min(day, finalDay));
  return start;
};

const attachSubscription = async (attempt: Attempt, subscription: Json) => {
  const subscriptionId = providerId(subscription.id, "sub");
  if (subscription.plan_id !== attempt.razorpay_plan_id || subscription.notes?.attempt_id !== attempt.id) {
    throw new HttpError(503, "Payment provider subscription mismatch");
  }
  const { error } = await db.from("billing_attempts").update({
    provider_subscription_id: subscriptionId,
    status: subscription.status,
    last_checked_at: new Date().toISOString(),
  }).eq("id", attempt.id);
  if (error) throw new HttpError(500, "Unable to save subscription");
};

const reconcileSubscription = async (attemptId: string) => {
  const { data: initial, error: initialError } = await db.from("billing_attempts").select("*").eq("id", attemptId).single();
  if (initialError || !initial) throw new HttpError(404, "Subscription not found");
  let attempt = initial as Attempt;
  if (!attempt.provider_subscription_id) {
    const created = Math.max(0, Math.floor(new Date(attempt.created_at).getTime() / 1000) - 60);
    const candidates = (await razorpayItems(`subscriptions?from=${created}`))
      .filter((item) => item.notes?.attempt_id === attempt.id);
    if (candidates.length > 1) throw new HttpError(503, "Ambiguous subscription requires support review");
    if (candidates.length === 0) return attempt;
    await attachSubscription(attempt, candidates[0]);
    attempt = (await db.from("billing_attempts").select("*").eq("id", attempt.id).single()).data as Attempt;
  }

  const subscriptionId = providerId(attempt.provider_subscription_id, "sub");
  const subscription = await razorpay("GET", `subscriptions/${subscriptionId}`);
  if (subscription.id !== subscriptionId || subscription.plan_id !== attempt.razorpay_plan_id) {
    throw new HttpError(503, "Payment provider subscription mismatch");
  }
  const invoices = await razorpayItems(`invoices?subscription_id=${encodeURIComponent(subscriptionId)}`);
  const seenPayments = new Set<string>();
  for (const invoice of invoices) {
    const start = invoice.billing_start;
    const end = invoice.billing_end;
    if (invoice.status !== "paid" || invoice.subscription_id !== subscriptionId
        || !Number.isInteger(start) || !Number.isInteger(end) || start <= 0 || end <= start
        || invoice.amount !== attempt.amount_paise || invoice.amount_paid !== attempt.amount_paise
        || invoice.amount_due !== 0 || invoice.currency !== "INR") continue;
    const paymentId = providerId(invoice.payment_id, "pay");
    const payment = await razorpay("GET", `payments/${paymentId}`);
    seenPayments.add(paymentId);
    if (payment.id !== paymentId || payment.invoice_id !== invoice.id
        || payment.amount !== attempt.amount_paise || payment.currency !== "INR"
        || !["captured", "refunded"].includes(payment.status)) continue;
    const revokedAt = payment.status === "refunded" || Number(payment.amount_refunded || 0) > 0
      ? new Date().toISOString() : null;
    const { error } = await db.from("billing_entitlements").upsert({
      user_id: null,
      account_id: attempt.account_id,
      attempt_id: attempt.id,
      provider: "razorpay",
      provider_subscription_id: subscriptionId,
      provider_payment_id: paymentId,
      provider_invoice_id: providerId(invoice.id, "inv"),
      amount_paise: attempt.amount_paise,
      currency: "INR",
      starts_at: new Date(start * 1000).toISOString(),
      expires_at: new Date(end * 1000).toISOString(),
      revoked_at: revokedAt,
    }, { onConflict: "provider,provider_payment_id" });
    if (error) throw new HttpError(500, "Unable to save entitlement");
  }

  const { data: activeGrants } = await db.from("billing_entitlements")
    .select("provider_payment_id").eq("attempt_id", attempt.id).is("revoked_at", null)
    .gt("expires_at", new Date().toISOString());
  for (const grant of activeGrants || []) {
    if (seenPayments.has(grant.provider_payment_id)) continue;
    const payment = await razorpay("GET", `payments/${providerId(grant.provider_payment_id, "pay")}`);
    if (payment.status === "refunded" || Number(payment.amount_refunded || 0) > 0) {
      await db.from("billing_entitlements").update({ revoked_at: new Date().toISOString() })
        .eq("provider", "razorpay").eq("provider_payment_id", grant.provider_payment_id);
    }
  }
  const nextStatus = TERMINAL.has(attempt.status) ? attempt.status : subscription.status;
  const { data: updated, error } = await db.from("billing_attempts").update({
    status: nextStatus,
    last_checked_at: new Date().toISOString(),
  }).eq("id", attempt.id).select().single();
  if (error) throw new HttpError(500, "Unable to update subscription");
  return updated as Attempt;
};

const attachOrder = async (attempt: Attempt, order: Json) => {
  const orderId = providerId(order.id, "order");
  if (order.receipt !== `tm_${attempt.id}` || order.notes?.attempt_id !== attempt.id
      || order.amount !== attempt.amount_paise || order.currency !== "INR") {
    throw new HttpError(503, "Payment provider order mismatch");
  }
  const { error } = await db.from("billing_attempts").update({
    provider_order_id: orderId,
    status: order.status,
    last_checked_at: new Date().toISOString(),
  }).eq("id", attempt.id);
  if (error) throw new HttpError(500, "Unable to save payment order");
};

const reconcileOneTime = async (attemptId: string) => {
  const { data: initial, error: initialError } = await db.from("billing_attempts").select("*").eq("id", attemptId).single();
  if (initialError || !initial) throw new HttpError(404, "Payment not found");
  let attempt = initial as Attempt;
  if (!attempt.provider_order_id) {
    const created = Math.max(0, Math.floor(new Date(attempt.created_at).getTime() / 1000) - 60);
    const candidates = (await razorpayItems(`orders?from=${created}`))
      .filter((item) => item.notes?.attempt_id === attempt.id && item.receipt === `tm_${attempt.id}`);
    if (candidates.length > 1) throw new HttpError(503, "Ambiguous payment requires support review");
    if (candidates.length === 0) return attempt;
    await attachOrder(attempt, candidates[0]);
    attempt = (await db.from("billing_attempts").select("*").eq("id", attempt.id).single()).data as Attempt;
  }

  const orderId = providerId(attempt.provider_order_id, "order");
  const order = await razorpay("GET", `orders/${orderId}`);
  if (order.id !== orderId || order.receipt !== `tm_${attempt.id}`
      || order.amount !== attempt.amount_paise || order.currency !== "INR") {
    throw new HttpError(503, "Payment provider order mismatch");
  }
  const payments = await razorpayItems(`orders/${orderId}/payments`);
  let confirmedPayment: Json | null = null;
  let refunded = false;
  for (const payment of payments) {
    if (payment.order_id !== orderId || payment.amount !== attempt.amount_paise
        || payment.currency !== "INR" || !["captured", "refunded"].includes(payment.status)) continue;
    const paymentId = providerId(payment.id, "pay");
    const authoritative = await razorpay("GET", `payments/${paymentId}`);
    if (authoritative.id !== paymentId || authoritative.order_id !== orderId
        || authoritative.amount !== attempt.amount_paise || authoritative.currency !== "INR"
        || !["captured", "refunded"].includes(authoritative.status)
        || !Number.isInteger(authoritative.created_at) || authoritative.created_at <= 0) continue;
    confirmedPayment = authoritative;
    refunded = authoritative.status === "refunded" || Number(authoritative.amount_refunded || 0) > 0;
    const startsAt = new Date(authoritative.created_at * 1000);
    const { error } = await db.from("billing_entitlements").upsert({
      user_id: null,
      account_id: attempt.account_id,
      attempt_id: attempt.id,
      provider: "razorpay",
      provider_subscription_id: null,
      provider_payment_id: paymentId,
      provider_invoice_id: null,
      amount_paise: attempt.amount_paise,
      currency: "INR",
      starts_at: startsAt.toISOString(),
      expires_at: oneTimeExpiry(authoritative.created_at, Number(attempt.access_months)).toISOString(),
      revoked_at: refunded ? new Date().toISOString() : null,
    }, { onConflict: "provider,provider_payment_id" });
    if (error) throw new HttpError(500, "Unable to save entitlement");
    break;
  }
  const status = confirmedPayment ? (refunded ? "refunded" : "completed")
    : (["created", "attempted", "paid"].includes(order.status) ? order.status : "failed");
  const { data: updated, error } = await db.from("billing_attempts").update({
    provider_payment_id: confirmedPayment?.id || attempt.provider_payment_id,
    status,
    last_checked_at: new Date().toISOString(),
  }).eq("id", attempt.id).select().single();
  if (error) throw new HttpError(500, "Unable to update payment");
  return updated as Attempt;
};

const reconcile = async (attemptId: string) => {
  const { data, error } = await db.from("billing_attempts").select("purchase_type").eq("id", attemptId).single();
  if (error || !data) throw new HttpError(404, "Payment not found");
  return data.purchase_type === "one_time" ? reconcileOneTime(attemptId) : reconcileSubscription(attemptId);
};

const entitlements = async (account: string, refresh = true) => {
  const { data: attempts, error } = await db.from("billing_attempts").select("*")
    .eq("account_id", account).order("created_at", { ascending: false });
  if (error) throw new HttpError(500, "Unable to load subscriptions");
  if (refresh) for (const attempt of attempts || []) await reconcile(attempt.id);
  const now = new Date();
  const { data: grant } = await db.from("billing_entitlements").select("expires_at,attempt_id")
    .eq("account_id", account).is("revoked_at", null)
    .lte("starts_at", now.toISOString()).gt("expires_at", now.toISOString())
    .order("expires_at", { ascending: false }).limit(1).maybeSingle();
  const { data: current } = await db.from("billing_attempts").select("*")
    .eq("account_id", account).order("created_at", { ascending: false });
  const serverTime = Math.floor(Date.now() / 1000);
  const expiry = grant ? Math.floor(new Date(grant.expires_at).getTime() / 1000) : 0;
  let purchaseType: "subscription" | "one_time" | null = null;
  if (grant?.attempt_id) {
    const { data: owner } = await db.from("billing_attempts").select("purchase_type")
      .eq("id", grant.attempt_id).maybeSingle();
    purchaseType = owner?.purchase_type || null;
  }
  return {
    account_id: account,
    active: Boolean(grant),
    expires_at: expiry,
    server_time: serverTime,
    lease_until: grant ? Math.min(expiry, serverTime + 3 * 86400) : 0,
    purchase_type: purchaseType,
    subscriptions: (current || []).filter((value) => value.purchase_type === "subscription")
      .map((value) => attemptView(value as Attempt)),
    purchases: (current || []).map((value) => attemptView(value as Attempt)),
  };
};

const TEST_PLANS = [
  { id: "monthly", title: "Monthly", amount_paise: 9900, period: "monthly", interval: 1, total_count: 120 },
  { id: "quarterly", title: "Quarterly", amount_paise: 14900, period: "monthly", interval: 3, total_count: 40 },
  { id: "six_monthly", title: "Six-monthly", amount_paise: 29900, period: "monthly", interval: 6, total_count: 20 },
  { id: "yearly", title: "Yearly", amount_paise: 49900, period: "yearly", interval: 1, total_count: 10 },
];

const ensureTestCatalog = async () => {
  if (!isTestMode()) return;
  const { data: published, error } = await db.from("billing_plans").select("id");
  if (error) throw new HttpError(500, "Unable to inspect test catalog");
  const publishedIds = new Set((published || []).map((item) => item.id));
  if (TEST_PLANS.every((plan) => publishedIds.has(plan.id))) return;
  const existing = await razorpayItems("plans");
  for (const plan of TEST_PLANS) {
    if (publishedIds.has(plan.id)) continue;
    const name = `TrackMint Test ${plan.title}`;
    let remote = existing.find((item) => item.period === plan.period && item.interval === plan.interval
      && item.item?.name === name && item.item?.amount === plan.amount_paise && item.item?.currency === "INR");
    if (!remote) {
      remote = await razorpay("POST", "plans", {
        period: plan.period,
        interval: plan.interval,
        item: { name, amount: plan.amount_paise, currency: "INR", description: `${plan.title} premium test plan` },
      });
    }
    const remoteId = providerId(remote.id, "plan");
    const { error: insertError } = await db.from("billing_plans").upsert({
      ...plan, currency: "INR", razorpay_plan_id: remoteId, active: true, catalog_version: 1,
    });
    if (insertError) throw new HttpError(500, "Unable to publish test catalog");
  }
};

const catalog = async () => {
  await ensureTestCatalog();
  const { data, error } = await db.from("billing_plans").select("id,title,amount_paise,currency,period,interval,total_count,catalog_version")
    .eq("active", true).order("amount_paise");
  if (error) throw new HttpError(500, "Unable to load plans");
  const version = String(Math.max(0, ...(data || []).map((item) => item.catalog_version)));
  return {
    version,
    checkout_enabled: checkoutEnabled() && hasCompleteCatalog(data || []),
    terms_url: Deno.env.get("BILLING_TERMS_URL")?.trim() || "",
    privacy_url: Deno.env.get("BILLING_PRIVACY_URL")?.trim() || "",
    support_url: Deno.env.get("BILLING_SUPPORT_URL")?.trim() || "",
    plans: (data || []).map((item) => ({ ...item, amount: item.amount_paise, amount_paise: undefined })),
  };
};

const publicConfiguration = () => ({
  auth_mode: configured("GOOGLE_WEB_CLIENT_ID") ? "google" : (isTestMode() ? "test" : "unavailable"),
  google_web_client_id: Deno.env.get("GOOGLE_WEB_CLIENT_ID")?.trim() || "",
  test_mode: isTestMode(),
});

const createTestSession = async () => {
  if (!isTestMode()) throw new HttpError(404, "Not found");
  const account = `test:${crypto.randomUUID()}`;
  const payload = base64Url(JSON.stringify({ account, exp: Math.floor(Date.now() / 1000) + 30 * 86400 }));
  return { account_id: account, token: `tm_test_${payload}.${await hmac(razorpayKeySecret(), payload)}` };
};

const readiness = async () => {
  const { count, error } = await db.from("billing_plans")
    .select("id", { count: "exact", head: true }).eq("active", true);
  if (error) throw new HttpError(500, "Unable to check billing readiness");
  const checks = {
    razorpay_key_id: configured("RAZORPAY_KEY_ID"),
    razorpay_key_secret: configured("RAZORPAY_KEY_SECRET"),
    razorpay_webhook_secret: configured("RAZORPAY_WEBHOOK_SECRET"),
    razorpay_account_id: configured("RAZORPAY_ACCOUNT_ID"),
    google_web_client_id: configured("GOOGLE_WEB_CLIENT_ID"),
    policy_urls: configured("BILLING_TERMS_URL") && configured("BILLING_PRIVACY_URL")
      && configured("BILLING_SUPPORT_URL"),
    four_active_plans: count === 4,
    checkout_enabled: Deno.env.get("BILLING_CHECKOUT_ENABLED") === "true",
  };
  return {
    ok: true,
    test_mode: configured("RAZORPAY_KEY_ID") && isTestMode(),
    test_checkout_ready: checks.razorpay_key_id && checks.razorpay_key_secret && checks.four_active_plans,
    production_ready: Object.values(checks).every(Boolean),
    checks,
  };
};

const createAttempt = async (request: Request, account: string, input: Json) => {
  const key = request.headers.get("Idempotency-Key") || "";
  if (!/^[A-Za-z0-9-]{16,80}$/.test(key)) throw new HttpError(400, "An idempotency key is required");
  if (Object.keys(input).sort().join(",") !== "catalog_version,plan_id,purchase_type"
      || typeof input.plan_id !== "string" || !["subscription", "one_time"].includes(input.purchase_type)) {
    throw new HttpError(400, "Only a catalog plan can be purchased");
  }
  const { data: existing } = await db.from("billing_attempts").select("*")
    .eq("account_id", account).eq("request_key", key).maybeSingle();
  if (existing) {
    if (existing.plan_id !== input.plan_id || existing.purchase_type !== input.purchase_type) {
      throw new HttpError(409, "Idempotency key already used for another payment");
    }
    return attemptView(await reconcile(existing.id));
  }
  if (!checkoutEnabled()) throw new HttpError(503, "New payments are temporarily unavailable");
  const { data: plan } = await db.from("billing_plans").select("*").eq("id", input.plan_id).eq("active", true).maybeSingle();
  if (!plan) throw new HttpError(400, "Unknown plan");
  if (String(plan.catalog_version) !== String(input.catalog_version)) throw new HttpError(409, "Prices changed; reload the plans");
  const access = await entitlements(account, false);
  if (access.active) throw new HttpError(409, "Your paid period is still active");
  const oneTime = input.purchase_type === "one_time";
  if (oneTime) {
    const { data: openMandate, error: openError } = await db.from("billing_attempts").select("id")
      .eq("account_id", account).eq("purchase_type", "subscription")
      .not("status", "in", `(${[...TERMINAL].join(",")})`).limit(1).maybeSingle();
    if (openError) throw new HttpError(500, "Unable to inspect existing AutoPay setup");
    if (openMandate) throw new HttpError(409, "Cancel your existing AutoPay setup before starting a one-time payment");
  }
  if (!oneTime) {
    const remotePlan = await razorpay("GET", `plans/${providerId(plan.razorpay_plan_id, "plan")}`);
    if (remotePlan.id !== plan.razorpay_plan_id || remotePlan.period !== plan.period
        || remotePlan.interval !== plan.interval || remotePlan.item?.amount !== plan.amount_paise
        || remotePlan.item?.currency !== "INR") throw new HttpError(503, "Merchant plan configuration does not match the catalog");
  }
  const accessMonths = plan.period === "yearly" ? plan.interval * 12 : plan.interval;
  if (oneTime && ![1, 3, 6, 12].includes(accessMonths)) throw new HttpError(503, "Unsupported prepaid access period");
  const { data: attempt, error } = await db.from("billing_attempts").insert({
    account_id: account,
    request_key: key,
    plan_id: plan.id,
    purchase_type: input.purchase_type,
    razorpay_plan_id: oneTime ? null : plan.razorpay_plan_id,
    access_months: oneTime ? accessMonths : null,
    amount_paise: plan.amount_paise,
    currency: "INR",
    status: "creating",
  }).select().single();
  if (error) {
    if (error.code === "23505") throw new HttpError(409, oneTime
      ? "This payment already exists; use Check status" : "A subscription already exists; use Check status or Cancel renewal");
    throw new HttpError(500, "Unable to reserve payment");
  }
  // Never retry this POST automatically. A timeout leaves a durable `creating`
  // reservation, which reconcile() recovers by matching notes.attempt_id.
  if (oneTime) {
    const order = await razorpay("POST", "orders", {
      amount: plan.amount_paise,
      currency: "INR",
      receipt: `tm_${attempt.id}`,
      notes: { attempt_id: attempt.id, account_id: account, plan_id: plan.id },
    });
    await attachOrder(attempt as Attempt, order);
  } else {
    const subscription = await razorpay("POST", "subscriptions", {
      plan_id: plan.razorpay_plan_id,
      total_count: plan.total_count,
      quantity: 1,
      customer_notify: true,
      expire_by: Math.floor(Date.now() / 1000) + 86400,
      notes: { attempt_id: attempt.id },
    });
    await attachSubscription(attempt as Attempt, subscription);
  }
  return attemptView(await loadAttempt(account, attempt.id));
};

const webhook = async (request: Request, raw: string) => {
  const supplied = request.headers.get("X-Razorpay-Signature") || "";
  const expected = await hmac(required("RAZORPAY_WEBHOOK_SECRET"), raw);
  if (!constantTimeEqual(expected, supplied)) throw new HttpError(400, "Invalid webhook signature");
  const eventId = request.headers.get("X-Razorpay-Event-Id") || "";
  if (!eventId || eventId.length > 200) throw new HttpError(400, "Missing event identifier");
  let event: Json;
  try { event = JSON.parse(raw); } catch (_) { throw new HttpError(400, "Invalid webhook body"); }
  if (event.account_id !== required("RAZORPAY_ACCOUNT_ID")) throw new HttpError(400, "Wrong merchant account");
  const rawSubscriptionId = event.payload?.subscription?.entity?.id
    || event.payload?.invoice?.entity?.subscription_id || null;
  const rawOrderId = event.payload?.order?.entity?.id
    || event.payload?.payment?.entity?.order_id || null;
  const rawPaymentId = event.payload?.payment?.entity?.id
    || event.payload?.refund?.entity?.payment_id || event.payload?.invoice?.entity?.payment_id || null;
  const subscriptionId = rawSubscriptionId ? providerId(rawSubscriptionId, "sub") : null;
  const orderId = rawOrderId ? providerId(rawOrderId, "order") : null;
  const paymentId = rawPaymentId ? providerId(rawPaymentId, "pay") : null;
  const bodyHash = await digest(raw);
  const { data: old } = await db.from("billing_webhook_events").select("body_sha256")
    .eq("provider", "razorpay").eq("provider_event_id", eventId).maybeSingle();
  if (old && old.body_sha256 !== bodyHash) throw new HttpError(400, "Event identifier conflict");
  const { error } = await db.from("billing_webhook_events").upsert({
    provider: "razorpay",
    provider_event_id: eventId,
    body_sha256: bodyHash,
    event_type: event.event,
    provider_subscription_id: subscriptionId,
    provider_order_id: orderId,
    provider_payment_id: paymentId,
  }, { onConflict: "provider,provider_event_id", ignoreDuplicates: true });
  if (error) throw new HttpError(500, "Unable to record webhook");
  if (subscriptionId || orderId || paymentId) {
    const { data: attempt } = await db.from("billing_attempts").select("id")
      .or([
        subscriptionId ? `provider_subscription_id.eq.${subscriptionId}` : "",
        orderId ? `provider_order_id.eq.${orderId}` : "",
        paymentId ? `provider_payment_id.eq.${paymentId}` : "",
      ].filter(Boolean).join(",")).maybeSingle();
    if (attempt) {
      try {
        await reconcile(attempt.id);
        await db.from("billing_webhook_events").update({ processed_at: new Date().toISOString(), processing_error: null })
          .eq("provider", "razorpay").eq("provider_event_id", eventId);
      } catch (failure) {
        await db.from("billing_webhook_events").update({ processing_error: failure instanceof Error ? failure.message : "Reconciliation failed" })
          .eq("provider", "razorpay").eq("provider_event_id", eventId);
      }
    }
  }
  return { received: true };
};

Deno.serve(async (request) => {
  try {
    const url = new URL(request.url);
    const marker = "/billing-api";
    const position = url.pathname.indexOf(marker);
    const path = position >= 0 ? url.pathname.slice(position + marker.length) || "/" : url.pathname;
    if (request.method === "GET" && path === "/health") return json(await readiness());
    // Public values needed before Google sign-in. No merchant secret is exposed.
    if (request.method === "GET" && path === "/v1/config") return json(publicConfiguration());
    if (request.method === "POST" && path === "/v1/test-session") return json(await createTestSession());
    if (request.method === "POST" && path === "/v1/webhooks/razorpay") {
      return json(await webhook(request, await request.text()));
    }
    const account = await authenticate(request);
    const raw = request.method === "GET" ? "" : await request.text();
    if (raw.length > 262_144) throw new HttpError(413, "Request too large");
    let input: Json = {};
    if (raw) {
      try { input = JSON.parse(raw); } catch (_) { throw new HttpError(400, "Invalid request"); }
      if (!input || Array.isArray(input) || typeof input !== "object") throw new HttpError(400, "Expected a JSON object");
    }
    if (request.method === "GET" && path === "/v1/catalog") return json(await catalog());
    if (request.method === "GET" && path === "/v1/me/entitlements") return json(await entitlements(account));
    if (request.method === "POST" && path === "/v1/subscription-attempts") {
      return json(await createAttempt(request, account, { ...input, purchase_type: "subscription" }));
    }
    if (request.method === "POST" && path === "/v1/purchase-attempts") return json(await createAttempt(request, account, input));
    const match = path.match(/^\/v1\/(?:subscription|purchase)-attempts\/([0-9a-f-]{36})(?:\/(verify|cancel))?$/);
    if (!match) throw new HttpError(404, "Not found");
    const attemptId = match[1];
    const action = match[2];
    if (request.method === "GET" && !action) return json(attemptView(await reconcile((await loadAttempt(account, attemptId)).id)));
    if (request.method === "POST" && action === "verify") {
      const attempt = await loadAttempt(account, attemptId);
      const paymentId = providerId(input.razorpay_payment_id, "pay");
      let signedReference: string;
      if (attempt.purchase_type === "one_time") {
        if (!attempt.provider_order_id || input.razorpay_order_id !== attempt.provider_order_id) {
          throw new HttpError(400, "Payment order mismatch");
        }
        signedReference = `${attempt.provider_order_id}|${paymentId}`;
      } else {
        if (!attempt.provider_subscription_id || input.razorpay_subscription_id !== attempt.provider_subscription_id) {
          throw new HttpError(400, "Subscription mismatch");
        }
        signedReference = `${paymentId}|${attempt.provider_subscription_id}`;
      }
      const expected = await hmac(razorpayKeySecret(), signedReference);
      if (!constantTimeEqual(expected, String(input.razorpay_signature || ""))) throw new HttpError(400, "Invalid checkout signature");
      await reconcile(attempt.id);
      return json(await entitlements(account, false));
    }
    if (request.method === "POST" && action === "cancel") {
      let attempt = await reconcile((await loadAttempt(account, attemptId)).id);
      if (attempt.purchase_type !== "subscription") throw new HttpError(409, "One-time payments do not renew");
      if (!TERMINAL.has(attempt.status) && !attempt.cancel_requested) {
        if (!attempt.provider_subscription_id) throw new HttpError(409, "Creation is being recovered; check status before cancelling");
        const result = await razorpay("POST", `subscriptions/${providerId(attempt.provider_subscription_id, "sub")}/cancel`, { cancel_at_cycle_end: 0 });
        if (result.id !== attempt.provider_subscription_id || result.status !== "cancelled") throw new HttpError(503, "Cancellation not confirmed; check status");
        const updated = await db.from("billing_attempts").update({ cancel_requested: true, status: "cancelled" })
          .eq("id", attempt.id).select().single();
        if (updated.error) throw new HttpError(500, "Unable to save cancellation");
        attempt = updated.data as Attempt;
      }
      return json(attemptView(attempt));
    }
    throw new HttpError(405, "Method not allowed");
  } catch (failure) {
    if (failure instanceof HttpError) return json({ error: failure.message }, failure.status);
    console.error(failure instanceof Error ? failure.message : "Unknown billing error");
    return json({ error: "Billing temporarily unavailable" }, 500);
  }
});
