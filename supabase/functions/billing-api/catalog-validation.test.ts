import { strict as assert } from "node:assert";
import { test } from "node:test";
import { hasCompleteCatalog } from "./catalog-validation.ts";

const plans = [
  { id: "monthly", period: "monthly", interval: 1, currency: "INR", amount_paise: 9900 },
  { id: "quarterly", period: "monthly", interval: 3, currency: "INR", amount_paise: 14900 },
  { id: "six_monthly", period: "monthly", interval: 6, currency: "INR", amount_paise: 29900 },
  { id: "yearly", period: "yearly", interval: 1, currency: "INR", amount_paise: 49900 },
];

test("all four schedules are ready regardless of order or additional products", () => {
  assert.equal(hasCompleteCatalog([...plans].reverse()), true);
  assert.equal(hasCompleteCatalog([...plans, { ...plans[0], id: "legacy" }]), true);
});
test("four rows cannot conceal a missing billing period", () => {
  assert.equal(hasCompleteCatalog([plans[0], plans[1], plans[2], { ...plans[0], id: "legacy" }]), false);
  assert.equal(hasCompleteCatalog([plans[0]]), false);
  assert.equal(hasCompleteCatalog([...plans, plans[0]]), false);
});
test("incorrect schedules, currencies, fractional or nonfinite prices fail closed", () => {
  for (const update of [{ interval: 1 }, { currency: "USD" }, { period: "weekly" },
    { amount_paise: 0 }, { amount_paise: 12.5 }, { amount_paise: NaN }, { amount_paise: Infinity }]) {
    assert.equal(hasCompleteCatalog(plans.map((plan) => plan.id === "quarterly" ? { ...plan, ...update } : plan)), false);
  }
});
