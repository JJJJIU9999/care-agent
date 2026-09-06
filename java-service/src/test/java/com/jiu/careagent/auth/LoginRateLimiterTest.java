package com.jiu.careagent.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {
    @Test
    void blocksTheSixthAttemptAndSuccessClearsTheWindow() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(limiter.retryAfterSeconds("127.0.0.1", "demo_user")).isZero();
            limiter.recordFailure("127.0.0.1", "demo_user");
        }

        assertThat(limiter.retryAfterSeconds("127.0.0.1", "DEMO_USER")).isPositive();
        limiter.clear("127.0.0.1", "demo_user");
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "demo_user")).isZero();
    }
}
