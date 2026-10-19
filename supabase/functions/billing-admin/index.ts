// Admin plan publisher. Deploy with JWT verification enabled. Razorpay keys and
// plan IDs live only in Edge Function Secrets; callers cannot override them.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.4";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});

const plans = {
  monthly: { title: "Monthly", amount_paise: 9900, period: "monthly", interval: 1, total_count: 120, env: "RAZORPAY_PLAN_MONTHLY" },
  quarterly: { title: "Quarterly", amount_paise: 14900, period: "monthly", interval: 3, total_count: 40, env: "RAZORPAY_PLAN_QUARTERLY" },
  six_monthly: { title: "Six-monthly", amount_paise: 29900, period: "monthly", interval: 6, total_count: 20, env: "RAZORPAY_PLAN_SIX_MONTHLY" },
  yearly: { title: "Yearly", amount_paise: 49900, period: "yearly", interval: 1, total_count: 10, env: "RAZORPAY_PLAN_YEARLY" },
} as const;

const platformKey = (currentName: string, legacyName: string) => {
  const current = Deno.env.get(currentName);
  if (current) {
    try { return JSON.parse(current).default as string; } catch (_) { return ""; }
  }
  return Deno.env.get(legacyName) || "";
};

Deno.serve(async (request) => {
  const authorization = request.headers.get("Authorization") || "";
  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = platformKey("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY");
  const adminKey = platformKey("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anon || !adminKey) return json({ error: "Supabase function configuration is incomplete" }, 503);
  const userClient = createClient(url, anon, { global: { headers: { Authorization: authorization } } });
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json({ error: "Sign in required" }, 401);

  const admin = createClient(url, adminKey);
  const { data: grant } = await admin.from("billing_admins").select("user_id")
    .eq("user_id", user.id).maybeSingle();
  if (!grant) return json({ error: "Admin access required" }, 403);
  if (request.method === "GET") {
    const { data, error } = await admin.from("billing_plans").select("*").order("id");
    return error ? json({ error: "Unable to load plans" }, 500) : json({ plans: data });
  }
  if (request.method !== "PUT") return json({ error: "Method not allowed" }, 405);
  try {
    const input = await request.json();
    if (!input || typeof input.id !== "string" || !(input.id in plans)
        || (input.active !== undefined && typeof input.active !== "boolean")
        || (input.catalog_version !== undefined
            && (!Number.isInteger(input.catalog_version) || input.catalog_version <= 0))) {
      return json({ error: "Invalid plan" }, 400);
    }
    const id = input.id as keyof typeof plans;
    const definition = plans[id];
    const keyId = Deno.env.get("RAZORPAY_KEY_ID") || "";
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET") || "";
    const razorpayPlanId = Deno.env.get(definition.env) || "";
    if (!keyId || !keySecret || !/^plan_[A-Za-z0-9]+$/.test(razorpayPlanId)) {
      return json({ error: "Razorpay test configuration is incomplete" }, 503);
    }
    const credentials = btoa(`${keyId}:${keySecret}`);
    const provider = await fetch(`https://api.razorpay.com/v1/plans/${razorpayPlanId}`, {
      headers: { Authorization: `Basic ${credentials}` },
    });
    const remote = await provider.json();
    if (!provider.ok || remote.id !== razorpayPlanId || remote.item?.amount !== definition.amount_paise
        || remote.item?.currency !== "INR" || remote.period !== definition.period
        || remote.interval !== definition.interval) {
      return json({ error: "Razorpay plan does not match the submitted catalog" }, 409);
    }
    const row = {
      id,
      title: definition.title,
      amount_paise: definition.amount_paise,
      currency: "INR",
      razorpay_plan_id: razorpayPlanId,
      period: definition.period,
      interval: definition.interval,
      total_count: definition.total_count,
      active: input.active ?? true,
      catalog_version: input.catalog_version ?? 1,
      updated_at: new Date().toISOString(),
      updated_by: user.id,
    };
    const { data, error } = await admin.from("billing_plans").upsert(row).select().single();
    return error ? json({ error: "Unable to publish plan" }, 500) : json({ plan: data });
  } catch (_) { return json({ error: "Invalid request" }, 400); }
});
