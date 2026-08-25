package com.example.spendtracker.data.local.entity;

import androidx.room.ColumnInfo;
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

    /** High-level tag like "#trip", "#wedding". Auto-derived from name if name starts with '#'. */
    @ColumnInfo(defaultValue = "")
    public String tag;

    public TransactionGroupEntity() {
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
        this.tag = "";
    }

    public TransactionGroupEntity(int id, String name, long startDate, long endDate) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
        this.tag = deriveTag(name);
    }

    /**
     * Derives a tag from a group name.
     * If name starts with '#', the first word (including '#') becomes the tag.
     * E.g. "Jaipur Trip" → "", "#trip expenses" → "#trip"
     */
    public static String deriveTag(String name) {
        if (name != null && name.startsWith("#")) {
            String[] parts = name.trim().split("\\s+", 2);
            return parts[0].toLowerCase();
        }
        return "";
    }
}
