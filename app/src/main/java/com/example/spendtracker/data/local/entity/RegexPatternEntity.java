package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "regex_patterns")
public class RegexPatternEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String bankName;
    public String pattern;
    public int amountGroup;
    public int typeGroup;

    public RegexPatternEntity() {}

    public RegexPatternEntity(int id, String bankName, String pattern, int amountGroup, int typeGroup) {
        this.id = id;
        this.bankName = bankName;
        this.pattern = pattern;
        this.amountGroup = amountGroup;
        this.typeGroup = typeGroup;
    }
}
