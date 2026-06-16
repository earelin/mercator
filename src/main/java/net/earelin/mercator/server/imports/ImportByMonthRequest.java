package net.earelin.mercator.server.imports;

import io.micronaut.serde.annotation.Serdeable;

/** Request body for a by-month import: an ISO year-month, {@code YYYY-MM}. */
@Serdeable
public record ImportByMonthRequest(String month) {
}
