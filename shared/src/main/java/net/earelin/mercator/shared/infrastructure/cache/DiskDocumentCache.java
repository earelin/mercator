package net.earelin.mercator.shared.infrastructure.cache;

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
import net.earelin.mercator.shared.domain.source.CachedDocument;
import net.earelin.mercator.shared.domain.source.DocumentCache;
import net.earelin.mercator.shared.domain.source.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disk-backed {@link DocumentCache}: each entry is two files under the cache directory &mdash; a
 * {@code .body} holding the exact downloaded bytes and a {@code .meta} holding the
 * {@link Representation} tag and charset. Keyed by the opaque {@code borme_id} (sanitised only for
 * filesystem safety; never parsed). An object-storage adapter is a future implementation of the
 * same port.
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
            Files.createDirectories(cacheDir);
            writeAtomic(bodyPath(bormeId), document.body());
            String meta = document.representation().name() + "\n" + document.charset().name() + "\n";
            writeAtomic(metaPath(bormeId), meta.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Best-effort cache: a write failure just means the next run re-fetches.
            LOG.warn("could not write cache entry for {}: {}", bormeId, e.toString());
        }
    }

    private void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path tmp = Files.createTempFile(cacheDir, "tmp-", ".part");
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
        return cacheDir.resolve(safeKey(bormeId) + ".body");
    }

    private Path metaPath(String bormeId) {
        return cacheDir.resolve(safeKey(bormeId) + ".meta");
    }

    /** Sanitise the opaque id into a single safe filename segment (no directory traversal). */
    private static String safeKey(String bormeId) {
        String safe = bormeId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            throw new UncheckedIOException(new IOException("empty cache key for borme_id: " + bormeId));
        }
        return safe;
    }
}
