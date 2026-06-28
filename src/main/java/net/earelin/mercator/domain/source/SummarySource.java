package net.earelin.mercator.domain.source;

import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Core-owned port (ADR-0014) the write paths call to discover which Secci&oacute;n A documents the
 * BORME published. It enumerates the BOE {@code datosabiertos} summary REST API and yields the
 * day's {@link DocumentDescriptor} list &mdash; the exact input {@link DocumentSource} consumes.
 *
 * <p>The implementation reuses the shared {@link BoeHttpClient} (one polite, rate-limited HTTP path
 * shared with document-fetch, ADR-0018); it does not open a second HTTP path. All outcomes are
 * returned as a {@link SummaryResult} value &mdash; a 404 day, an empty day and a give-up never
 * throw.
 */
public interface SummarySource {

    /**
     * Enumerate one day's Secci&oacute;n A documents.
     *
     * @param date the publication day
     * @return {@link SummaryResult.Published} (possibly empty) on 200,
     *         {@link SummaryResult.NotPublished} on 404, or {@link SummaryResult.Failed} on give-up
     */
    SummaryResult enumerate(LocalDate date);

    /**
     * Enumerate an inclusive date range, one {@link SummaryResult} per day in calendar order. The
     * stream is <strong>lazy</strong>: each day is fetched only as the stream is consumed, so a
     * caller can process and discard day by day. Non-publication (404) and empty days surface as
     * their respective results and are the caller's to skip; iterating a range is always safe and
     * stateless.
     *
     * @param start        the first day (inclusive)
     * @param endInclusive the last day (inclusive)
     * @return one result per day in {@code [start, endInclusive]}; empty if {@code endInclusive}
     *         precedes {@code start}
     */
    default Stream<SummaryResult> enumerate(LocalDate start, LocalDate endInclusive) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(endInclusive, "endInclusive");
        if (endInclusive.isBefore(start)) {
            return Stream.empty();
        }
        return start.datesUntil(endInclusive.plusDays(1)).map(this::enumerate);
    }

    /**
     * Enumerate from {@code start} up to and including <strong>today</strong>, resolved in the BOE's
     * publication calendar ({@code Europe/Madrid}) so the boundary day is never off by one.
     *
     * @param start the first day (inclusive)
     * @return one lazy result per day in {@code [start, today]}
     */
    Stream<SummaryResult> enumerateToToday(LocalDate start);
}
