package dev.ayo.devinbridge.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevinStatusTest {

    @Test
    void startingUpStatusesAreWorking() {
        assertEquals(DevinStatus.WORKING, DevinStatus.fromRaw("new", null));
        assertEquals(DevinStatus.WORKING, DevinStatus.fromRaw("claimed", null));
        assertEquals(DevinStatus.WORKING, DevinStatus.fromRaw("resuming", null));
    }

    @Test
    void runningWithWorkingOrNullDetailIsWorking() {
        assertEquals(DevinStatus.WORKING, DevinStatus.fromRaw("running", "working"));
        assertEquals(DevinStatus.WORKING, DevinStatus.fromRaw("running", null));
    }

    @Test
    void runningWithWaitingDetailIsBlocked() {
        assertEquals(DevinStatus.BLOCKED, DevinStatus.fromRaw("running", "waiting_for_user"));
        assertEquals(DevinStatus.BLOCKED, DevinStatus.fromRaw("running", "waiting_for_approval"));
    }

    @Test
    void runningWithFinishedDetailIsFinished() {
        assertEquals(DevinStatus.FINISHED, DevinStatus.fromRaw("running", "finished"));
    }

    @Test
    void suspendedIsExpiredRegardlessOfReason() {
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("suspended", "inactivity"));
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("suspended", "usage_limit_exceeded"));
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("suspended", "out_of_credits"));
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("suspended", null));
    }

    @Test
    void errorAndExitAreExpired() {
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("error", null));
        assertEquals(DevinStatus.EXPIRED, DevinStatus.fromRaw("exit", null));
    }

    @Test
    void unrecognizedStatusIsUnknown() {
        assertEquals(DevinStatus.UNKNOWN, DevinStatus.fromRaw("some-future-status", null));
        assertEquals(DevinStatus.UNKNOWN, DevinStatus.fromRaw(null, null));
    }

    @Test
    void unrecognizedStatusDetailUnderRunningIsUnknown() {
        assertEquals(DevinStatus.UNKNOWN, DevinStatus.fromRaw("running", "some-future-detail"));
    }
}
