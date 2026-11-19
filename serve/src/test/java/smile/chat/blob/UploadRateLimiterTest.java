/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UploadRateLimiter}.
 */
public class UploadRateLimiterTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advanceSeconds(long seconds) {
            now.updateAndGet(t -> t.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    public void testGivenSlidingWindowWhenExceedingLimitThenBlockedUntilWindowPasses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(2, clock, 100);

        assertTrue(limiter.tryAcquire("198.51.100.1").allowed());
        clock.advanceSeconds(10);
        assertTrue(limiter.tryAcquire("198.51.100.1").allowed());

        // 3rd attempt within 60s must be rejected with Retry-After ~50s
        UploadRateLimiter.Decision blocked = limiter.tryAcquire("198.51.100.1");
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() >= 49 && blocked.retryAfterSeconds() <= 51,
                "Expected ~50s retry-after, got " + blocked.retryAfterSeconds());

        // Different IP is unaffected
        assertTrue(limiter.tryAcquire("198.51.100.2").allowed());

        // Advance past the first request's 60s window
        clock.advanceSeconds(51);
        assertTrue(limiter.tryAcquire("198.51.100.1").allowed());
    }

    @Test
    public void testGivenManyRotatingIpsWhenLimitReachedThenMapRemainsBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(5, clock, 4);

        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryAcquire("203.0.113." + i).allowed());
            clock.advanceSeconds(1);
        }
        assertTrue(limiter.trackedKeyCount() <= 4,
                "Tracked keys must not exceed configured max bound");
    }

    @Test
    public void testGivenZeroLimitWhenTryAcquireThenAlwaysAllowed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(0, clock, 10);
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire("198.51.100.9").allowed());
        }
    }
}
