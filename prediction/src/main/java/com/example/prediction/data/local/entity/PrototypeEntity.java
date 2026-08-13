package com.example.prediction.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prototypes")
public class PrototypeEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String category;
    public float[] vector;
    
    // Original transaction features for reference/debugging
    public String merchantName;
    public String upiId;
    public double amount;
    public String type; // DEBIT/CREDIT
    public int dayOfWeek;
    public int hourOfDay;

    public PrototypeEntity() {}

    public PrototypeEntity(String category, float[] vector, String merchantName, String upiId, double amount, String type, int dayOfWeek, int hourOfDay) {
        this.category = category;
        this.vector = vector;
        this.merchantName = merchantName;
        this.upiId = upiId;
        this.amount = amount;
        this.type = type;
        this.dayOfWeek = dayOfWeek;
        this.hourOfDay = hourOfDay;
    }
}
