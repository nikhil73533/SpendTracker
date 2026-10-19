/** Validate identities and schedules, not just row count; amounts stay merchant-owned. */
type CatalogPlan = {
  id: string;
  period: string;
  interval: number;
  currency: string;
  amount_paise: number;
};

export const hasCompleteCatalog = (plans: CatalogPlan[]): boolean => {
  const expected = [
    { id: "monthly", period: "monthly", interval: 1 },
    { id: "quarterly", period: "monthly", interval: 3 },
    { id: "six_monthly", period: "monthly", interval: 6 },
    { id: "yearly", period: "yearly", interval: 1 },
  ];
  return expected.every((required) => {
    const matches = plans.filter((plan) => plan.id === required.id);
    return matches.length === 1 && matches[0].period === required.period
      && matches[0].interval === required.interval && matches[0].currency === "INR"
      && Number.isSafeInteger(matches[0].amount_paise) && matches[0].amount_paise > 0;
  });
};
