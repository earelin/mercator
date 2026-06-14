package net.earelin.mercator.shared.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void spaces_permits_evenly_at_configured_rate() {
        long[] now = {0L};
        LongSupplier clock = () -> now[0];
        List<Long> sleeps = new ArrayList<>();
        TokenBucketRateLimiter.Sleeper sleeper = nanos -> {
            sleeps.add(nanos);
            now[0] += nanos; // advancing the virtual clock models the sleep elapsing
        };

        // 10 permits/s → one permit every 100ms (100_000_000 ns).
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, clock, sleeper);

        limiter.acquire(); // first permit is immediate
        limiter.acquire();
        limiter.acquire();

        assertEquals(List.of(100_000_000L, 100_000_000L), sleeps);
    }

    @Test
    void does_not_sleep_when_requests_are_naturally_spaced_out() {
        long[] now = {0L};
        LongSupplier clock = () -> now[0];
        List<Long> sleeps = new ArrayList<>();
        TokenBucketRateLimiter.Sleeper sleeper = sleeps::add;

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, clock, sleeper);

        limiter.acquire();
        now[0] += 500_000_000L; // caller already waited 500ms before the next request
        limiter.acquire();

        assertTrue(sleeps.isEmpty(), "no throttling needed when caller is already slow");
    }

    @Test
    void rejects_non_positive_rate() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0.0));
    }
}
