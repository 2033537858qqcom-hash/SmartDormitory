package com.dormitory.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionPersistenceTest {
    @Test
    void sharedStorePreservesSessionsAcrossServiceRestartAndSupportsRevocation() {
        SessionStore store = new TestSessionStore();
        SessionService beforeRestart = new SessionService(store);
        String token = beforeRestart.create(7L);
        SessionService afterRestart = new SessionService(store);
        assertEquals(7L, afterRestart.getUserId(token));
        afterRestart.revokeUser(7L);
        assertNull(beforeRestart.getUserId(token));
    }

}
