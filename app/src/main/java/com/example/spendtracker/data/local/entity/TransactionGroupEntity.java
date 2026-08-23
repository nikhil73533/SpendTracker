package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transaction_groups",
        indices = {
            @Index(value = {"startDate", "endDate"}),
            @Index(value = {"createdAt"})
        })
public class TransactionGroupEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public long startDate;
    public long endDate;
    public long createdAt;
    public boolean isActive;

    public TransactionGroupEntity() {
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
    }

    public TransactionGroupEntity(int id, String name, long startDate, long endDate) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }
}
