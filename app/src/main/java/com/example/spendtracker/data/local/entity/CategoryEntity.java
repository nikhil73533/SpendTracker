package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories", indices = {@Index(value = {"name", "type"}, unique = true)})
public class CategoryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String icon;
    public boolean isDefault;
    public String type; // EXPENSE or INCOME

    public CategoryEntity() {
        this.type = "EXPENSE";
    }

    public CategoryEntity(int id, String name, String icon, boolean isDefault) {
        this(id, name, icon, isDefault, "EXPENSE");
    }

    public CategoryEntity(int id, String name, String icon, boolean isDefault, String type) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isDefault = isDefault;
        this.type = type != null ? type : "EXPENSE";
    }
}
