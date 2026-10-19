package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;

/** Credit/debit provenance takes precedence over counterparty or account names. */
public final class TransferDirection {
    private TransferDirection() {}
    public static boolean isIncoming(Transaction t) {
        return "CREDIT".equalsIgnoreCase(t.getDirection()) ||
                ("UNKNOWN".equalsIgnoreCase(t.getDirection()) && "INCOME".equalsIgnoreCase(t.getType()));
    }
    public static boolean isTransfer(Transaction t) {
        return "TRANSFER".equalsIgnoreCase(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory());
    }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()
                && !"Unknown".equalsIgnoreCase(value.trim())) return value.trim();
        return "";
    }
    public static String from(Transaction t, String userName) {
        String owner = first(t.getBankName(), userName, "You");
        return first(t.getFromAccount(), isIncoming(t) ? first(t.getSender(), t.getReceiverName(), t.getUpiId(), "Unknown sender") : owner);
    }
    public static String to(Transaction t, String userName) {
        String owner = first(t.getBankName(), userName, "You");
        return first(t.getToAccount(), isIncoming(t) ? owner : first(t.getReceiverName(), t.getSender(), t.getUpiId(), "Unknown receiver"));
    }
    public static void fillEndpoints(Transaction t, String userName) {
        if (!isTransfer(t)) return;
        t.setFromAccount(from(t, userName));
        t.setToAccount(to(t, userName));
    }
    public static void normalize(Transaction t) {
        if ("UNKNOWN".equalsIgnoreCase(t.getDirection())) {
            t.setDirection("INCOME".equalsIgnoreCase(t.getType()) ? "CREDIT" : "DEBIT");
        }
        if ("Transfer".equalsIgnoreCase(t.getCategory())) t.setType("TRANSFER");
    }
}
