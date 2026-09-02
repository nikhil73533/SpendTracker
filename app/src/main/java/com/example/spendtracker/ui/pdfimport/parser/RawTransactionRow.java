package com.example.spendtracker.ui.pdfimport.parser;

public class RawTransactionRow {
    private String dateStr;
    private String narration;
    private String referenceNo;
    private String upiId;
    private Double debitAmount;
    private Double creditAmount;
    private Double balance;
    private String rawLine;

    public RawTransactionRow() {
    }

    public RawTransactionRow(String dateStr, String narration, String referenceNo, String upiId,
                             Double debitAmount, Double creditAmount, Double balance, String rawLine) {
        this.dateStr = dateStr;
        this.narration = narration;
        this.referenceNo = referenceNo;
        this.upiId = upiId;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.balance = balance;
        this.rawLine = rawLine;
    }

    public String getDateStr() {
        return dateStr != null ? dateStr : "";
    }

    public void setDateStr(String dateStr) {
        this.dateStr = dateStr;
    }

    public String getNarration() {
        return narration != null ? narration : "";
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public String getReferenceNo() {
        return referenceNo != null ? referenceNo : "";
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getUpiId() {
        return upiId != null ? upiId : "";
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public Double getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(Double debitAmount) {
        this.debitAmount = debitAmount;
    }

    public Double getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(Double creditAmount) {
        this.creditAmount = creditAmount;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public String getRawLine() {
        return rawLine != null ? rawLine : "";
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }
}
