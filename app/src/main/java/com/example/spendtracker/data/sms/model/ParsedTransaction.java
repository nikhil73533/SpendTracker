package com.example.spendtracker.data.sms.model;

/**
 * Intermediate representation of a parsed transaction, populated field-by-field
 * by the extraction pipeline before being converted into a domain {@code Transaction}.
 *
 * <p>Each field has an associated confidence score. Fields with {@code null} values
 * indicate that extraction was not possible or not attempted.
 */
public class ParsedTransaction {

    // ── Core fields ──────────────────────────────────────────────────────────
    private Double amount;
    private double amountConfidence;

    private String transactionType;       // EXPENSE, INCOME, TRANSFER
    private double transactionTypeConfidence;

    private String transactionStatus;     // SUCCESS, FAILED, PENDING, REVERSED
    private double transactionStatusConfidence;

    private String merchant;
    private double merchantConfidence;

    private String bankName;
    private double bankConfidence;

    private String sourceType;            // Account, Credit Card, Wallet, UPI
    private double sourceTypeConfidence;

    // ── Account / card ───────────────────────────────────────────────────────
    private String accountSuffix;         // e.g. "1234" from XX1234
    private String fromAccount;           // For transfers
    private String toAccount;             // For transfers

    // ── UPI / reference ──────────────────────────────────────────────────────
    private String upiId;                 // VPA (e.g. merchant@upi)
    private String referenceId;           // Numeric transaction/reference ID

    // ── Date / time ──────────────────────────────────────────────────────────
    private Long parsedDate;              // From SMS body (may be null)
    private long smsTimestamp;            // From SMS metadata (always set)

    // ── SMS metadata ─────────────────────────────────────────────────────────
    private String senderAddress;
    private String rawMessage;

    // ── Fees ─────────────────────────────────────────────────────────────────
    private double fees;

    // ── Getters & setters ────────────────────────────────────────────────────

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public double getAmountConfidence() { return amountConfidence; }
    public void setAmountConfidence(double amountConfidence) { this.amountConfidence = amountConfidence; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public double getTransactionTypeConfidence() { return transactionTypeConfidence; }
    public void setTransactionTypeConfidence(double conf) { this.transactionTypeConfidence = conf; }

    public String getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(String transactionStatus) { this.transactionStatus = transactionStatus; }

    public double getTransactionStatusConfidence() { return transactionStatusConfidence; }
    public void setTransactionStatusConfidence(double conf) { this.transactionStatusConfidence = conf; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public double getMerchantConfidence() { return merchantConfidence; }
    public void setMerchantConfidence(double merchantConfidence) { this.merchantConfidence = merchantConfidence; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public double getBankConfidence() { return bankConfidence; }
    public void setBankConfidence(double bankConfidence) { this.bankConfidence = bankConfidence; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public double getSourceTypeConfidence() { return sourceTypeConfidence; }
    public void setSourceTypeConfidence(double conf) { this.sourceTypeConfidence = conf; }

    public String getAccountSuffix() { return accountSuffix; }
    public void setAccountSuffix(String accountSuffix) { this.accountSuffix = accountSuffix; }

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public Long getParsedDate() { return parsedDate; }
    public void setParsedDate(Long parsedDate) { this.parsedDate = parsedDate; }

    public long getSmsTimestamp() { return smsTimestamp; }
    public void setSmsTimestamp(long smsTimestamp) { this.smsTimestamp = smsTimestamp; }

    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public double getFees() { return fees; }
    public void setFees(double fees) { this.fees = fees; }

    /** Returns the best available date: body-parsed date if available, else SMS timestamp. */
    public long getEffectiveDate() {
        return parsedDate != null ? parsedDate : smsTimestamp;
    }

    /**
     * Calculates an overall confidence score using weighted critical fields.
     * Amount and transaction type carry the most weight.
     */
    public double getOverallConfidence() {
        double weightedSum = 0;
        double totalWeight = 0;

        // Critical fields (high weight)
        if (amount != null) {
            weightedSum += amountConfidence * 3.0;
            totalWeight += 3.0;
        }
        if (transactionType != null) {
            weightedSum += transactionTypeConfidence * 3.0;
            totalWeight += 3.0;
        }

        // Important fields (medium weight)
        if (bankName != null) {
            weightedSum += bankConfidence * 1.5;
            totalWeight += 1.5;
        }
        if (sourceType != null) {
            weightedSum += sourceTypeConfidence * 1.0;
            totalWeight += 1.0;
        }

        // Optional fields (low weight)
        if (merchant != null) {
            weightedSum += merchantConfidence * 1.0;
            totalWeight += 1.0;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
}
