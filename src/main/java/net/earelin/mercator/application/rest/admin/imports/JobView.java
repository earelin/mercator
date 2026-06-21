package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.serde.annotation.Serdeable;
import net.earelin.mercator.domain.ingest.ImportJob;

/** JSON view of an {@link ImportJob} returned by the import endpoints. */
@Serdeable
public record JobView(
        String jobId,
        String kind,
        String target,
        String status,
        String detail,
        String statusUrl) {

    static JobView of(ImportJob job) {
        return new JobView(
                job.id(),
                job.kind().name(),
                job.target(),
                job.status().name(),
                job.detail(),
                HistoricalImportController.statusUrl(job.id()));
    }
}
