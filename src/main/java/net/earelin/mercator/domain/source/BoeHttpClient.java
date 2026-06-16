package net.earelin.mercator.domain.source;

import java.net.URI;

/**
 * Driven port for a single polite GET against the BOE. The implementation applies the shared rate
 * limiter, the descriptive User-Agent, and the bounded exponential-backoff retry policy (ADR-0018)
 * before returning a final {@link HttpFetchResult}. The core stays unaware of {@code java.net.http}.
 */
public interface BoeHttpClient {

    /**
     * Fetch {@code uri}, returning its body on success or a classified failure once the retry
     * policy has given up. Never throws for an HTTP/network error &mdash; those become
     * {@link HttpFetchResult.Failure} so the fetch chain stays fail-soft.
     */
    HttpFetchResult get(URI uri);
}
