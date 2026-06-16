package net.earelin.mercator.server.imports;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.earelin.mercator.domain.ingest.BulkMergeIngestionService;
import net.earelin.mercator.domain.ingest.IngestionService;
import net.earelin.mercator.domain.ingest.JobStore;

/**
 * Wires the framework-free {@link BulkMergeIngestionService} into the Micronaut context, keeping the
 * Micronaut annotations in the {@code server} layer and the domain core dependency-free (ADR-0014).
 */
@Factory
public class IngestionFactory {

    @Singleton
    IngestionService ingestionService(JobStore jobStore) {
        return new BulkMergeIngestionService(jobStore);
    }
}
