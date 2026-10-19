package com.example.spendtracker.domain.model.analytics;

/**
 * Spending velocity: daily spending rate and comparison with historical averages.
 */
public class ForecastResult {
    private final double spentSoFar;
    private final int elapsedDays;
    private final int totalDaysInPeriod;
    private final double dailyRate;
    private final double projectedTotal;
    private final double previousMonthDailyAverage;
    private final double threeMonthDailyAverage;
    private final double sixMonthDailyAverage;
    private final String velocityInsight;

    public ForecastResult(double spentSoFar, int elapsedDays, int totalDaysInPeriod,
                          double previousMonthDailyAverage, double threeMonthDailyAverage,
                          double sixMonthDailyAverage) {
        this.spentSoFar = spentSoFar;
        this.elapsedDays = Math.max(elapsedDays, 1); // Avoid division by zero
        this.totalDaysInPeriod = totalDaysInPeriod;
        this.dailyRate = spentSoFar / this.elapsedDays;
        this.projectedTotal = this.dailyRate * totalDaysInPeriod;
        this.previousMonthDailyAverage = previousMonthDailyAverage;
        this.threeMonthDailyAverage = threeMonthDailyAverage;
        this.sixMonthDailyAverage = sixMonthDailyAverage;

        // Generate velocity insight
        if (threeMonthDailyAverage > 0) {
            double diff = ((dailyRate - threeMonthDailyAverage) / threeMonthDailyAverage) * 100;
            if (Math.abs(diff) < 5) {
                this.velocityInsight = "Your spending pace is in line with your 3-month average.";
            } else if (diff > 0) {
                this.velocityInsight = String.format("Your current spending pace is %.0f%% higher than your 3-month average.", diff);
            } else {
                this.velocityInsight = String.format("Your current spending pace is %.0f%% lower than your 3-month average.", Math.abs(diff));
            }
        } else {
            this.velocityInsight = "Not enough historical data for pace comparison.";
        }
    }

    public double getSpentSoFar() { return spentSoFar; }
    public int getElapsedDays() { return elapsedDays; }
    public int getTotalDaysInPeriod() { return totalDaysInPeriod; }
    public double getDailyRate() { return dailyRate; }
    public double getProjectedTotal() { return projectedTotal; }
    public double getPreviousMonthDailyAverage() { return previousMonthDailyAverage; }
    public double getThreeMonthDailyAverage() { return threeMonthDailyAverage; }
    public double getSixMonthDailyAverage() { return sixMonthDailyAverage; }
    public String getVelocityInsight() { return velocityInsight; }
}
