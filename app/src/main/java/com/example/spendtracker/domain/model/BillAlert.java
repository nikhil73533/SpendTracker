package com.example.spendtracker.domain.model;

/**
 * Domain model for proactive bill alerts.
 */
public class BillAlert {
    private final int id;
    private final String sender;
    private final String template;
    private final String lastMessage;
    private final int occurrenceCount;
    private final long lastSeen;
    private final double amount;
    private final boolean isResolved;

    public BillAlert(int id, String sender, String template, String lastMessage, int occurrenceCount, long lastSeen, double amount, boolean isResolved) {
        this.id = id;
        this.sender = sender;
        this.template = template;
        this.lastMessage = lastMessage;
        this.occurrenceCount = occurrenceCount;
        this.lastSeen = lastSeen;
        this.amount = amount;
        this.isResolved = isResolved;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getTemplate() { return template; }
    public String getLastMessage() { return lastMessage; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public long getLastSeen() { return lastSeen; }
    public double getAmount() { return amount; }
    public boolean isResolved() { return isResolved; }
}
