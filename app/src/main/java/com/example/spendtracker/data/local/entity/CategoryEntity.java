package com.example.spendtracker.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing a spending or income category in Room database.
 * Includes budget range configurations (weekly, monthly, annually) and notification toggle.
 */
@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name = "";
    public String icon = "";
    public boolean isDefault;

    @ColumnInfo(defaultValue = "EXPENSE")
    public String type = "EXPENSE"; // EXPENSE or INCOME

    // Budget range fields
    @ColumnInfo(defaultValue = "1")
    public boolean unlimitedWeekly = true;

    @ColumnInfo(defaultValue = "0.0")
    public double weeklyBudget = 0.0;

    @ColumnInfo(defaultValue = "1")
    public boolean unlimitedMonthly = true;

    @ColumnInfo(defaultValue = "0.0")
    public double monthlyBudget = 0.0;

    @ColumnInfo(defaultValue = "1")
    public boolean unlimitedAnnually = true;

    @ColumnInfo(defaultValue = "0.0")
    public double annuallyBudget = 0.0;

    @ColumnInfo(defaultValue = "1")
    public boolean notificationsEnabled = true;

    /** Default no-arg constructor required by Room. */
    public CategoryEntity() {
        this.name = "";
        this.icon = "";
        this.type = "EXPENSE";
    }

    /** Legacy constructor for basic category creation. */
    public CategoryEntity(int id, String name, String icon, boolean isDefault) {
        this(id, name, icon, isDefault, "EXPENSE");
    }

    /** Constructor with type specification. */
    public CategoryEntity(int id, String name, String icon, boolean isDefault, String type) {
        this.id = id;
        this.name = name != null ? name : "";
        this.icon = icon != null ? icon : "";
        this.isDefault = isDefault;
        this.type = type != null ? type : "EXPENSE";
    }

    /**
     * Full constructor including budget ranges and notification preferences.
     */
    public CategoryEntity(int id, String name, String icon, boolean isDefault, String type,
                          boolean unlimitedWeekly, double weeklyBudget,
                          boolean unlimitedMonthly, double monthlyBudget,
                          boolean unlimitedAnnually, double annuallyBudget,
                          boolean notificationsEnabled) {
        this.id = id;
        this.name = name != null ? name : "";
        this.icon = icon != null ? icon : "";
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
}
