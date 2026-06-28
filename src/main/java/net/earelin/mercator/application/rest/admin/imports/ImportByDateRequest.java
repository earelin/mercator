package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Request body for a by-date import: an ISO date, {@code YYYY-MM-DD}. */
@Serdeable
public record ImportByDateRequest(@Nullable String date) {
}
