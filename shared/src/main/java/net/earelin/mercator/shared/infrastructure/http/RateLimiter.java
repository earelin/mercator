package net.earelin.mercator.shared.infrastructure.http;

/**
 * A global request-rate gate. Every outbound BOE request acquires a permit first, so no amount of
 * internal concurrency can exceed the configured ceiling (ADR-0018). A single instance is shared
 * across document-fetch <em>and</em> summary-enumeration &mdash; one budget, not two.
 */
public interface RateLimiter {

    /** Block until a permit is available, then consume it. */
    void acquire();
}
