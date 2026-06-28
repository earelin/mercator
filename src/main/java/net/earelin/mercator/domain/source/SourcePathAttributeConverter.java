package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

/**
 * Maps {@link SourcePath} to the exact {@code borme_log.source_path} text ({@code backfill} /
 * {@code daily_incremental}) and back, so the {@link BormeLogEntry} entity can carry the enum
 * directly while the column keeps its lowercase vocabulary (the CHECK constraint, baseline schema).
 * Without this, Micronaut Data would persist the enum {@code name()} ({@code BACKFILL}…) and violate
 * the constraint.
 */
@Singleton
public final class SourcePathAttributeConverter implements AttributeConverter<SourcePath, String> {

    @Override
    public @Nullable String convertToPersistedValue(SourcePath entityValue, ConversionContext context) {
        return entityValue == null ? null : entityValue.dbValue();
    }

    @Override
    public @Nullable SourcePath convertToEntityValue(String persistedValue, ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        for (SourcePath candidate : SourcePath.values()) {
            if (candidate.dbValue().equals(persistedValue)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown source_path value: " + persistedValue);
    }
}
