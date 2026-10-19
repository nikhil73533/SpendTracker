package com.example.spendtracker.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.Entity;
/** UTC-day materialization; partial boundary days are read from the transaction index. */
@Entity(tableName = "analytics_daily_type", primaryKeys = {"day", "type", "bucket"})
public class DailyTypeAggregate {
    public long day;
    @NonNull public String type = "";
    @NonNull public String bucket = "";
    public double total;
    public long transactionCount;
}
