package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity for storing proactive bill alerts detected by repeating SMS patterns.
 */
@Entity(tableName = "bill_alerts")
public class BillAlertEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** SMS sender address or name. */
    public String sender;

    /** Normalized message template used for matching. */
    public String template;

    /** The last raw message received matching this template. */
    public String lastMessage;

    /** Number of times this template has been seen. */
    public int occurrenceCount;

    /** Timestamp of the last occurrence. */
    public long lastSeen;

    /** Potential amount extracted from the message. */
    public double amount;

    /** Whether the user has resolved/dismissed this alert. */
    public boolean isResolved;

    public BillAlertEntity() {}

    public BillAlertEntity(String sender, String template, String lastMessage, int occurrenceCount, long lastSeen, double amount) {
        this.sender = sender;
        this.template = template;
        this.lastMessage = lastMessage;
        this.occurrenceCount = occurrenceCount;
        this.lastSeen = lastSeen;
        this.amount = amount;
        this.isResolved = false;
    }
}
