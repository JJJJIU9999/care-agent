package com.jiu.careagent.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, FailureWindow> failures = new ConcurrentHashMap<>();

    public long retryAfterSeconds(String ip, String username) {
        String key = key(ip, username);
        Instant now = Instant.now();
        FailureWindow current = failures.get(key);
        if (current == null || current.expiresAt().isBefore(now)) {
            failures.remove(key, current);
            return 0;
        }
        return current.count() >= MAX_FAILURES ? Math.max(1, Duration.between(now, current.expiresAt()).toSeconds()) : 0;
    }

    public void recordFailure(String ip, String username) {
        String key = key(ip, username);
        Instant now = Instant.now();
        failures.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new FailureWindow(1, now.plus(WINDOW));
            }
            return new FailureWindow(current.count() + 1, current.expiresAt());
        });
    }

    public void clear(String ip, String username) {
        failures.remove(key(ip, username));
    }

    private String key(String ip, String username) {
        String normalizedIp = ip == null ? "unknown" : ip.trim();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizedIp + '\n' + normalizedUsername;
    }

    private record FailureWindow(int count, Instant expiresAt) {
    }
}
