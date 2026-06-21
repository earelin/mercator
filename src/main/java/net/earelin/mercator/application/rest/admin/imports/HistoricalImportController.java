package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import net.earelin.mercator.domain.ingest.ImportJob;
import net.earelin.mercator.domain.ingest.IngestionService;
import net.earelin.mercator.domain.ingest.JobStore;

/**
 * Admin endpoint that triggers a massive historical import by date or month (ADR-0005, ADR-0006).
 * It is <strong>off the public read namespace</strong> and gated by
 * {@code mercator.imports.historical.enabled}: when disabled the routes are <em>absent</em> (404),
 * not merely forbidden. A request is accepted asynchronously — the POST returns {@code 202} with a
 * job id and a {@code Location}/status URL to poll; the import itself runs in the background
 * (scaffold: see {@code BulkMergeIngestionService}). The import endpoint must always require the
 * API key, including in local dev where reads are anonymous (ADR-0013) — enforced once
 * {@code micronaut-security} lands.
 */
@Requires(property = "mercator.imports.historical.enabled", value = "true")
@Controller("/admin/imports")
@ExecuteOn(TaskExecutors.BLOCKING)
public class HistoricalImportController {

    static final String BASE_PATH = "/admin/imports";

    private final IngestionService ingestionService;
    private final JobStore jobStore;

    public HistoricalImportController(IngestionService ingestionService, JobStore jobStore) {
        this.ingestionService = ingestionService;
        this.jobStore = jobStore;
    }

    static String statusUrl(String jobId) {
        return BASE_PATH + "/" + jobId;
    }

    @Post("/by-date")
    public HttpResponse<?> importByDate(@Body ImportByDateRequest request) {
        final LocalDate date;
        try {
            date = LocalDate.parse(request.date());
        } catch (DateTimeParseException | NullPointerException e) {
            return badRequest("date must be an ISO date (YYYY-MM-DD)");
        }
        return accepted(ingestionService.importByDate(date));
    }

    @Post("/by-month")
    public HttpResponse<?> importByMonth(@Body ImportByMonthRequest request) {
        final YearMonth month;
        try {
            month = YearMonth.parse(request.month());
        } catch (DateTimeParseException | NullPointerException e) {
            return badRequest("month must be an ISO year-month (YYYY-MM)");
        }
        return accepted(ingestionService.importByMonth(month));
    }

    @Get("/{jobId}")
    public HttpResponse<JobView> status(@PathVariable String jobId) {
        return jobStore.find(jobId)
                .map(job -> HttpResponse.ok(JobView.of(job)))
                .orElseGet(HttpResponse::notFound);
    }

    private static HttpResponse<JobView> accepted(ImportJob job) {
        return HttpResponse.accepted(URI.create(statusUrl(job.id()))).body(JobView.of(job));
    }

    private static HttpResponse<ErrorResponse> badRequest(String detail) {
        return HttpResponse.badRequest(
                new ErrorResponse(HttpStatus.BAD_REQUEST.getCode(), "Bad Request", detail));
    }

    /** Problem shape shared with the read API: {@code { status, error, detail }}. */
    @Serdeable
    public record ErrorResponse(int status, String error, String detail) {
    }
}
