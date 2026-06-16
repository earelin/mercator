package net.earelin.mercator.infrastructure.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import net.earelin.mercator.domain.source.CachedDocument;
import net.earelin.mercator.domain.source.DocumentCache;
import net.earelin.mercator.domain.source.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disk-backed {@link DocumentCache}: each entry is two files &mdash; a {@code .body} holding the
 * exact downloaded bytes and a {@code .meta} holding the {@link Representation} tag and charset.
 *
 * <p>To avoid piling every document into one directory (a full backfill is hundreds of thousands
 * of documents), entries are sharded into nested folders derived from the dash-separated parts of
 * the id: {@code BORME-A-2024-1-01} is stored under {@code BORME/A/2024/1/BORME-A-2024-1-01.body}.
 * This is a <em>filesystem fan-out only</em> &mdash; the cache key remains the full opaque
 * {@code borme_id} (used verbatim as the leaf filename); the split makes no padding/structure
 * assumption and is never used for identity (ADR-0008, summary-enumeration). An object-storage
 * adapter is a future implementation of the same port.
 */
public final class DiskDocumentCache implements DocumentCache {

    private static final Logger LOG = LoggerFactory.getLogger(DiskDocumentCache.class);

    private final Path cacheDir;

    public DiskDocumentCache(Path cacheDir) {
        this.cacheDir = cacheDir.toAbsolutePath().normalize();
    }

    @Override
    public Optional<CachedDocument> get(String bormeId) {
        Path bodyPath = bodyPath(bormeId);
        Path metaPath = metaPath(bormeId);
        if (!Files.isRegularFile(bodyPath) || !Files.isRegularFile(metaPath)) {
            return Optional.empty();
        }
        try {
            List<String> meta = Files.readAllLines(metaPath, StandardCharsets.UTF_8);
            if (meta.size() < 2) {
                LOG.warn("malformed cache meta for {}; treating as miss", bormeId);
                return Optional.empty();
            }
            Representation representation = Representation.valueOf(meta.get(0).trim());
            Charset charset = Charset.forName(meta.get(1).trim());
            byte[] body = Files.readAllBytes(bodyPath);
            return Optional.of(new CachedDocument(representation, body, charset));
        } catch (IOException | RuntimeException e) {
            LOG.warn("could not read cache entry for {}; treating as miss: {}", bormeId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(String bormeId, CachedDocument document) {
        try {
            Files.createDirectories(entryDir(bormeId));
            writeAtomic(bodyPath(bormeId), document.body());
            String meta = document.representation().name() + "\n" + document.charset().name() + "\n";
            writeAtomic(metaPath(bormeId), meta.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Best-effort cache: a write failure just means the next run re-fetches.
            LOG.warn("could not write cache entry for {}: {}", bormeId, e.toString());
        }
    }

    private void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), "tmp-", ".part");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Path bodyPath(String bormeId) {
        return entryDir(bormeId).resolve(safeKey(bormeId) + ".body");
    }

    private Path metaPath(String bormeId) {
        return entryDir(bormeId).resolve(safeKey(bormeId) + ".meta");
    }

    /**
     * The shard directory for an id: the dash-separated parts except the last become nested
     * folders (so {@code BORME-A-2024-1-01} &rarr; {@code <cache>/BORME/A/2024/1}). Purely a
     * storage layout; the leaf file is still keyed by the full opaque id.
     */
    private Path entryDir(String bormeId) {
        String[] parts = bormeId.split("-");
        Path dir = cacheDir;
        for (int i = 0; i < parts.length - 1; i++) {
            String segment = sanitizeSegment(parts[i]);
            if (!segment.isEmpty()) {
                dir = dir.resolve(segment);
            }
        }
        return dir;
    }

    /** Sanitise one path segment so it is safe and can never escape the cache directory. */
    private static String sanitizeSegment(String segment) {
        String safe = segment.replaceAll("[^A-Za-z0-9._]", "_");
        return safe.equals(".") || safe.equals("..") ? "_" : safe;
    }

    /** Sanitise the opaque id into a single safe leaf filename (keeps dashes; no traversal). */
    private static String safeKey(String bormeId) {
        String safe = bormeId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            throw new UncheckedIOException(new IOException("empty cache key for borme_id: " + bormeId));
        }
        return safe;
    }
}
