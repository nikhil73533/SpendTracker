package com.example.spendtracker.data.sms.config;

import java.util.Collections;
import java.util.List;

/**
 * POJO representing a bank-specific parsing configuration loaded from JSON.
 */
public class BankConfig {
    private String bankName;
    private List<String> senders;
    private List<PatternConfig> patterns;

    public BankConfig() {}

    public BankConfig(String bankName, List<String> senders, List<PatternConfig> patterns) {
        this.bankName = bankName;
        this.senders = senders;
        this.patterns = patterns;
    }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public List<String> getSenders() { return senders != null ? senders : Collections.emptyList(); }
    public void setSenders(List<String> senders) { this.senders = senders; }

    public List<PatternConfig> getPatterns() { return patterns != null ? patterns : Collections.emptyList(); }
    public void setPatterns(List<PatternConfig> patterns) { this.patterns = patterns; }

    /**
     * A single regex pattern configuration within a bank config.
     */
    public static class PatternConfig {
        private String name;
        private String regex;
        private int amountGroup;
        private int accountGroup;
        private int receiverGroup;
        private int upiGroup;
        private int dateGroup;
        private String type;       // EXPENSE, INCOME, TRANSFER
        private String sourceType; // Account, Credit Card, etc.

        public PatternConfig() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }

        public int getAmountGroup() { return amountGroup; }
        public void setAmountGroup(int amountGroup) { this.amountGroup = amountGroup; }

        public int getAccountGroup() { return accountGroup; }
        public void setAccountGroup(int accountGroup) { this.accountGroup = accountGroup; }

        public int getReceiverGroup() { return receiverGroup; }
        public void setReceiverGroup(int receiverGroup) { this.receiverGroup = receiverGroup; }

        public int getUpiGroup() { return upiGroup; }
        public void setUpiGroup(int upiGroup) { this.upiGroup = upiGroup; }

        public int getDateGroup() { return dateGroup; }
        public void setDateGroup(int dateGroup) { this.dateGroup = dateGroup; }

        public String getType() { return type != null ? type : "EXPENSE"; }
        public void setType(String type) { this.type = type; }

        public String getSourceType() { return sourceType != null ? sourceType : "Account"; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    }
}
