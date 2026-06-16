package net.earelin.mercator.server.imports;

import io.micronaut.serde.annotation.Serdeable;

/** Request body for a by-date import: an ISO date, {@code YYYY-MM-DD}. */
@Serdeable
public record ImportByDateRequest(String date) {
}
