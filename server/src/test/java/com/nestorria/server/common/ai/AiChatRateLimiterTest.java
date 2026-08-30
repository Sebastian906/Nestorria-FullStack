package com.nestorria.server.common.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiChatRateLimiterTest {

    private AiChatRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new AiChatRateLimiter();
    }

    @Test
    void checkLimit_firstMessage_succeeds() {
        assertDoesNotThrow(() -> rateLimiter.checkLimit("user-1"));
    }

    @Test
    void checkLimit_underLimit_succeeds() {
        for (int i = 0; i < 19; i++) {
            rateLimiter.checkLimit("user-1");
        }
        assertDoesNotThrow(() -> rateLimiter.checkLimit("user-1"));
    }

    @Test
    void checkLimit_atLimit_throws() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.checkLimit("user-1");
        }
        assertThrows(AiServiceException.class,
            () -> rateLimiter.checkLimit("user-1"));
    }

    @Test
    void checkLimit_differentUsers_independent() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.checkLimit("user-1");
        }
        // user-2 still has full quota
        assertDoesNotThrow(() -> rateLimiter.checkLimit("user-2"));
    }

    @Test
    void remainingMessages_freshUser_returns20() {
        assertEquals(20, rateLimiter.remainingMessages("user-1"));
    }

    @Test
    void remainingMessages_after5_returns15() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit("user-1");
        }
        assertEquals(15, rateLimiter.remainingMessages("user-1"));
    }

    @Test
    void remainingMessages_atLimit_returns0() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.checkLimit("user-1");
        }
        assertEquals(0, rateLimiter.remainingMessages("user-1"));
    }
}
