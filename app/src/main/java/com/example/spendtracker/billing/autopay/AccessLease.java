package com.example.spendtracker.billing.autopay;

/** Account-bound server lease. Clock rollback and elapsed-time expiry fail closed. */
public final class AccessLease {
    private static final long MAX_LEASE_MS = 3L * 24 * 60 * 60 * 1000;
    public final String account;
    public final long receivedAt, receivedElapsed, duration, expiresAt;

    public AccessLease(String account, long receivedAt, long receivedElapsed,
                       long serverTime, long leaseUntil, long expiresAt) {
        this.account = account;
        this.receivedAt = receivedAt;
        this.receivedElapsed = receivedElapsed;
        this.expiresAt = expiresAt;
        this.duration = Math.max(0, Math.min(MAX_LEASE_MS, Math.min(leaseUntil, expiresAt) - serverTime));
    }

    public boolean allows(String currentAccount, long now, long elapsed) {
        return account != null && account.equals(currentAccount) && duration > 0
                && now >= receivedAt && now - receivedAt < duration
                && elapsed >= receivedElapsed && elapsed - receivedElapsed < duration;
    }
}
