package com.example.spendtracker.domain.service;

import com.example.spendtracker.domain.model.analytics.FinancialInsight;
import com.example.spendtracker.domain.model.analytics.ForecastResult;
import com.example.spendtracker.domain.model.analytics.SpendingConcentration;
import com.example.spendtracker.domain.model.analytics.MonthlyComparison;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InsightEngine {

    private final AnalyticsService analyticsService;

    @Inject
    public InsightEngine(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public List<FinancialInsight> generateInsights(long start, long end) {
        List<FinancialInsight> insights = new ArrayList<>();
        
        MonthlyComparison comp = analyticsService.getMonthOverMonthComparison(start, end);
        if (comp.getDifference() > 0) {
            insights.add(new FinancialInsight("Your spending has increased by " + String.format("%.1f", comp.getPercentChange()) + "% compared to the previous period.", 
                FinancialInsight.Priority.HIGH, FinancialInsight.InsightType.SPENDING_INCREASE, comp.getDifference()));
        } else if (comp.getDifference() < 0) {
            insights.add(new FinancialInsight("Great job! Your spending decreased by " + String.format("%.1f", Math.abs(comp.getPercentChange())) + "% compared to the previous period.", 
                FinancialInsight.Priority.MEDIUM, FinancialInsight.InsightType.SPENDING_DECREASE, Math.abs(comp.getDifference())));
        }

        SpendingConcentration conc = analyticsService.getSpendingConcentration(start, end);
        if (conc.getConcentrationPercentage() > 50) {
            insights.add(new FinancialInsight("More than 50% of your spending is concentrated in the top 3 categories.", 
                FinancialInsight.Priority.MEDIUM, FinancialInsight.InsightType.CONCENTRATION, conc.getConcentrationPercentage()));
        }

        if (insights.isEmpty()) {
            insights.add(new FinancialInsight("Your spending is stable. No major anomalies detected.", 
                FinancialInsight.Priority.LOW, FinancialInsight.InsightType.GENERAL, 0));
        }

        return insights;
    }

    public ForecastResult getSpendingForecast(long start, long end) {
        return analyticsService.getSpendingVelocity(start, end);
    }
}
