package com.processing.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private static final int CAPACITY = 2;
    private static final double REFILL_PER_SECOND = 1.0;
    private static final String CLIENT_A = "POST /api/transactions:10.0.0.1";
    private static final String CLIENT_B = "POST /api/transactions:10.0.0.2";

    private final AtomicLong nowNanos = new AtomicLong();

    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = InMemoryRateLimiter.forTesting(
                CAPACITY, REFILL_PER_SECOND, Duration.ofMinutes(10), 100, nowNanos::get);
    }

    @Test
    void shouldAllowRequestWhenBucketHasTokens() {
        // Act
        boolean allowed = rateLimiter.allowRequest(CLIENT_A);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectRequestWhenBucketCapacityIsExhausted() {
        // Arrange
        exhaustBucket(CLIENT_A);

        // Act
        boolean allowed = rateLimiter.allowRequest(CLIENT_A);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldAllowRequestAgainWhenTokenIsRefilledAfterOneSecond() {
        // Arrange
        exhaustBucket(CLIENT_A);
        nowNanos.addAndGet(TimeUnit.SECONDS.toNanos(1));

        // Act
        boolean allowed = rateLimiter.allowRequest(CLIENT_A);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldKeepSeparateBucketsWhenKeysAreDifferent() {
        // Arrange
        exhaustBucket(CLIENT_A);

        // Act
        boolean allowed = rateLimiter.allowRequest(CLIENT_B);

        // Assert
        assertThat(allowed).isTrue();
    }

    private void exhaustBucket(String key) {
        rateLimiter.allowRequest(key);
        rateLimiter.allowRequest(key);
    }
}
