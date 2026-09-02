package com.example.spendtracker.domain.model;

/**
 * Domain model representing a Category with its name, icon, budget limits, and notification preferences.
 */
public class Category {
    private int id;
    private String name;
    private String icon;
    private boolean isDefault;
    private String type;
    private boolean unlimitedWeekly;
    private double weeklyBudget;
    private boolean unlimitedMonthly;
    private double monthlyBudget;
    private boolean unlimitedAnnually;
    private double annuallyBudget;
    private boolean notificationsEnabled;

    /** Constructor for legacy/basic category instantiation. */
    public Category(int id, String name, String icon, boolean isDefault) {
        this(id, name, icon, isDefault, "EXPENSE", true, 0.0, true, 0.0, true, 0.0, true);
    }

    /** Full constructor with all budget attributes. */
    public Category(int id, String name, String icon, boolean isDefault, String type,
                    boolean unlimitedWeekly, double weeklyBudget,
                    boolean unlimitedMonthly, double monthlyBudget,
                    boolean unlimitedAnnually, double annuallyBudget,
                    boolean notificationsEnabled) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isDefault = isDefault;
        this.type = type != null ? type : "EXPENSE";
        this.unlimitedWeekly = unlimitedWeekly;
        this.weeklyBudget = weeklyBudget;
        this.unlimitedMonthly = unlimitedMonthly;
        this.monthlyBudget = monthlyBudget;
        this.unlimitedAnnually = unlimitedAnnually;
        this.annuallyBudget = annuallyBudget;
        this.notificationsEnabled = notificationsEnabled;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public boolean isDefault() { return isDefault; }
    public String getType() { return type; }

    public boolean isUnlimitedWeekly() { return unlimitedWeekly; }
    public double getWeeklyBudget() { return weeklyBudget; }
    public boolean isUnlimitedMonthly() { return unlimitedMonthly; }
    public double getMonthlyBudget() { return monthlyBudget; }
    public boolean isUnlimitedAnnually() { return unlimitedAnnually; }
    public double getAnnuallyBudget() { return annuallyBudget; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    public void setUnlimitedWeekly(boolean unlimitedWeekly) { this.unlimitedWeekly = unlimitedWeekly; }
    public void setWeeklyBudget(double weeklyBudget) { this.weeklyBudget = weeklyBudget; }
    public void setUnlimitedMonthly(boolean unlimitedMonthly) { this.unlimitedMonthly = unlimitedMonthly; }
    public void setMonthlyBudget(double monthlyBudget) { this.monthlyBudget = monthlyBudget; }
    public void setUnlimitedAnnually(boolean unlimitedAnnually) { this.unlimitedAnnually = unlimitedAnnually; }
    public void setAnnuallyBudget(double annuallyBudget) { this.annuallyBudget = annuallyBudget; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
}
