package com.example.spendtracker.billing.autopay;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccessLeaseTest {
    private AccessLease lease() { return new AccessLease("alice", 1000, 500, 10000, 11000, 12000); }
    @Test public void validAccountHasAccessWithinLease() { assertTrue(lease().allows("alice", 1500, 1000)); }
    @Test public void differentAccountCannotUseLease() { assertFalse(lease().allows("bob",1500,1000)); }
    @Test public void signedOutCannotUseLease() { assertFalse(lease().allows(null,1500,1000)); }
    @Test public void exactExpiryRevokesAccess() { assertFalse(lease().allows("alice",2000,1500)); }
    @Test public void wallClockRollbackFailsClosed() { assertFalse(lease().allows("alice",999,1000)); }
    @Test public void elapsedClockResetFailsClosed() { assertFalse(lease().allows("alice",1500,499)); }
    @Test public void stoppedWallClockCannotExtendLease() { assertFalse(lease().allows("alice",1000,1500)); }
    @Test public void leaseIsCappedAtPaidExpiry() {
        AccessLease value = new AccessLease("alice",1000,500,10000,50000,10500);
        assertTrue(value.allows("alice",1499,999)); assertFalse(value.allows("alice",1500,1000));
    }
    @Test public void excessiveServerLeaseIsCappedAtThreeDays() {
        AccessLease value = new AccessLease("alice",1000,500,10000,Long.MAX_VALUE,Long.MAX_VALUE);
        assertEquals(259200000L, value.duration);
    }
    @Test public void expiredOrEmptyLeaseNeverGrants() {
        assertFalse(new AccessLease("alice",1000,500,10000,9999,12000).allows("alice",1000,500));
        assertFalse(new AccessLease("alice",1000,500,10000,0,0).allows("alice",1000,500));
    }
}
