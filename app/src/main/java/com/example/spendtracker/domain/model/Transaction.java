package com.example.spendtracker.domain.model;

public class Transaction {
    private int id;
    private double amount;
    private String category;
    private String categoryEmoji;
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
    private int transactionGroupId;
    private String transactionGroupName; // Transient, populated from join
    private String status;
    private long deletedAt;
    /** Transient: ML prediction confidence score (0.0–1.0), not persisted to DB */
    private double confidenceScore;

    public Transaction() {
        this.status = "ACTIVE";
        this.deletedAt = 0;
        this.transactionGroupId = 0;
        this.categoryEmoji = "";
        this.confidenceScore = 1.0; // Default: fully confident (user-entered or confirmed)
    }

    public Transaction(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType) {
        this(id, amount, category, "", description, type, date, source, sender, upiId, receiverName, bankName, sourceType, null, null, 0.0);
    }

    public Transaction(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this(id, amount, category, "", description, type, date, source, sender, upiId, receiverName, bankName, sourceType, fromAccount, toAccount, fees);
    }

    public Transaction(int id, double amount, String category, String categoryEmoji, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.categoryEmoji = categoryEmoji != null ? categoryEmoji : "";
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
        this.status = "ACTIVE";
        this.deletedAt = 0;
        this.transactionGroupId = 0;
    }

    public int getId() { return id; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCategory() { return category != null ? category : ""; }
    public void setCategory(String category) { this.category = category != null ? category : ""; }
    public String getCategoryName() { return getCategory(); }
    public String getCategoryEmoji() { return categoryEmoji != null ? categoryEmoji : ""; }
    public void setCategoryEmoji(String categoryEmoji) { this.categoryEmoji = categoryEmoji != null ? categoryEmoji : ""; }
    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description != null ? description : ""; }
    public String getType() { return type != null ? type : "EXPENSE"; }
    public void setType(String type) { this.type = type != null ? type : "EXPENSE"; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public String getSource() { return source != null ? source : ""; }
    public void setSource(String source) { this.source = source != null ? source : ""; }
    public String getSender() { return sender != null ? sender : ""; }
    public void setSender(String sender) { this.sender = sender != null ? sender : ""; }
    public String getUpiId() { return upiId != null ? upiId : ""; }
    public void setUpiId(String upiId) { this.upiId = upiId != null ? upiId : ""; }
    public String getReceiverName() { return receiverName != null ? receiverName : ""; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName != null ? receiverName : ""; }
    public String getBankName() { return bankName != null ? bankName : ""; }
    public void setBankName(String bankName) { this.bankName = bankName != null ? bankName : ""; }
    public String getSourceType() { return sourceType != null ? sourceType : ""; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType != null ? sourceType : ""; }
    public String getFromAccount() { return fromAccount != null ? fromAccount : ""; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount != null ? fromAccount : ""; }
    public String getToAccount() { return toAccount != null ? toAccount : ""; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount != null ? toAccount : ""; }
    public double getFees() { return fees; }
    public void setFees(double fees) { this.fees = fees; }
    public int getTransactionGroupId() { return transactionGroupId; }
    public void setTransactionGroupId(int transactionGroupId) { this.transactionGroupId = transactionGroupId; }
    public String getTransactionGroupName() { return transactionGroupName != null ? transactionGroupName : ""; }
    public void setTransactionGroupName(String transactionGroupName) { this.transactionGroupName = transactionGroupName != null ? transactionGroupName : ""; }
    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status != null ? status : "ACTIVE"; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}
