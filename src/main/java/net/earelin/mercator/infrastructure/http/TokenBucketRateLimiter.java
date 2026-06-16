package net.earelin.mercator.infrastructure.http;

import java.util.function.LongSupplier;

/**
 * A simple, fair, smoothed-rate limiter: permits are spaced evenly at {@code 1/permitsPerSecond}
 * apart (no bursting), which keeps the BOE seeing a steady low-rate client (ADR-0018). Thread-safe;
 * intended to be created once and shared across all BOE HTTP access.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    /** Pluggable sleep, so tests can run without real wall-clock delays. */
    @FunctionalInterface
    interface Sleeper {
        void sleepNanos(long nanos) throws InterruptedException;
    }

    private final long intervalNanos;
    private final LongSupplier clock;
    private final Sleeper sleeper;

    /** Earliest time (nanos, on {@code clock}'s timeline) the next permit may be granted. */
    private long nextFreeNanos;

    public TokenBucketRateLimiter(double permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime, TokenBucketRateLimiter::realSleep);
    }

    TokenBucketRateLimiter(double permitsPerSecond, LongSupplier clock, Sleeper sleeper) {
        if (!(permitsPerSecond > 0.0) || Double.isInfinite(permitsPerSecond)) {
            throw new IllegalArgumentException("permitsPerSecond must be finite and > 0");
        }
        this.intervalNanos = (long) Math.ceil(1_000_000_000.0 / permitsPerSecond);
        this.clock = clock;
        this.sleeper = sleeper;
        this.nextFreeNanos = clock.getAsLong();
    }

    @Override
    public void acquire() {
        long waitNanos;
        synchronized (this) {
            long now = clock.getAsLong();
            long grantAt = Math.max(now, nextFreeNanos);
            nextFreeNanos = grantAt + intervalNanos;
            waitNanos = grantAt - now;
        }
        if (waitNanos > 0) {
            try {
                sleeper.sleepNanos(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void realSleep(long nanos) throws InterruptedException {
        Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
    }
}
