package com.example.spendtracker.domain.model.analytics;

import java.util.List;

public class SpendingConcentration {
    public static class Contributor {
        private final String name;
        private final double amount;
        private final double percentage;

        public Contributor(String name, double amount, double percentage) {
            this.name = name;
            this.amount = amount;
            this.percentage = percentage;
        }

        public String getName() { return name; }
        public double getAmount() { return amount; }
        public double getPercentage() { return percentage; }
    }

    private final double totalAmount;
    private final List<Contributor> topContributors;
    private final double concentrationPercentage;

    public SpendingConcentration(double totalAmount, List<Contributor> topContributors, double concentrationPercentage) {
        this.totalAmount = totalAmount;
        this.topContributors = topContributors;
        this.concentrationPercentage = concentrationPercentage;
    }

    public double getTotalAmount() { return totalAmount; }
    public List<Contributor> getTopContributors() { return topContributors; }
    public double getConcentrationPercentage() { return concentrationPercentage; }
}
