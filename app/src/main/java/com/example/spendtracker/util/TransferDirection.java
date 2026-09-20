package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;

/** Credit/debit provenance takes precedence over counterparty or account names. */
public final class TransferDirection {
    private TransferDirection() {}
    public static boolean isIncoming(Transaction t) {
        return "CREDIT".equalsIgnoreCase(t.getDirection()) ||
                ("UNKNOWN".equalsIgnoreCase(t.getDirection()) && "INCOME".equalsIgnoreCase(t.getType()));
    }
    public static void normalize(Transaction t) {
        if ("UNKNOWN".equalsIgnoreCase(t.getDirection())) {
            t.setDirection("INCOME".equalsIgnoreCase(t.getType()) ? "CREDIT" : "DEBIT");
        }
        if ("Transfer".equalsIgnoreCase(t.getCategory())) t.setType("TRANSFER");
    }
}
