package com.example.spendtracker.domain.model;

public class Transaction {
    private int id;
    private double amount;
    private String categoryName;
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

    public Transaction() {
        this.status = "ACTIVE";
        this.deletedAt = 0;
        this.transactionGroupId = 0;
    }

    public Transaction(int id, double amount, String categoryName, String categoryEmoji, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType) {
        this(id, amount, categoryName, categoryEmoji, description, type, date, source, sender, upiId, receiverName, bankName, sourceType, null, null, 0.0);
    }

    public Transaction(int id, double amount, String categoryName, String categoryEmoji, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this.id = id;
        this.amount = amount;
        this.categoryName = categoryName;
        this.categoryEmoji = categoryEmoji;
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
    public void setId(int id) { this.id = id; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryEmoji() { return categoryEmoji; }
    public void setCategoryEmoji(String categoryEmoji) { this.categoryEmoji = categoryEmoji; }
    /** Legacy getter for convenience (joins name and emoji) */
    public String getCategory() {
        if (categoryEmoji == null || categoryEmoji.isEmpty()) return categoryName;
        return categoryEmoji + " " + categoryName;
    }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }
    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }
    public double getFees() { return fees; }
    public void setFees(double fees) { this.fees = fees; }
    public int getTransactionGroupId() { return transactionGroupId; }
    public void setTransactionGroupId(int transactionGroupId) { this.transactionGroupId = transactionGroupId; }
    public String getTransactionGroupName() { return transactionGroupName; }
    public void setTransactionGroupName(String transactionGroupName) { this.transactionGroupName = transactionGroupName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
}
