package net.earelin.mercator.infrastructure.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Micronaut Data mapping of one {@code borme_log} row (baseline schema). Property names map to the
 * snake_case columns by the default naming strategy ({@code bormeId → borme_id}, …); the explicit
 * {@code @MappedEntity("borme_log")} pins the table so it is not derived as {@code borme_log_record}.
 *
 * <p>The enum-backed columns ({@code status}, {@code error_kind}, {@code source_path}) are stored as
 * their {@code TEXT} forms and mapped here as {@link String}, so the domain↔storage translation
 * stays in the adapter ({@link MicronautDataBormeLog}) and out of the persistence row.
 */
@MappedEntity("borme_log")
public record BormeLogRecord(
        @Id String bormeId,
        LocalDate pubDate,
        String status,
        @Nullable String errorKind,
        String sourcePath,
        @Nullable String errorDetail,
        Instant processedAt) {
}
