package com.example.spendtracker.domain.model;

public class Transaction {
    private int id;
    private double amount;
    private String category;
    private String description;
    private String type;
    private long date;
    private String source;
    private String sender;
    private String upiId;
    private String receiverName;
    private String bankName;
    private String sourceType;
    private String fromAccount;
    private String toAccount;
    private double fees;

    public Transaction(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType) {
        this(id, amount, category, description, type, date, source, sender, upiId, receiverName, bankName, sourceType, null, null, 0.0);
    }

    public Transaction(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.type = type;
        this.date = date;
        this.source = source;
        this.sender = sender;
        this.upiId = upiId;
        this.receiverName = receiverName;
        this.bankName = bankName;
        this.sourceType = sourceType;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.fees = fees;
    }

    public int getId() { return id; }
    public double getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public long getDate() { return date; }
    public String getSource() { return source; }
    public String getSender() { return sender; }
    public String getUpiId() { return upiId; }
    public String getReceiverName() { return receiverName; }
    public String getBankName() { return bankName; }
    public String getSourceType() { return sourceType; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public double getFees() { return fees; }
}
