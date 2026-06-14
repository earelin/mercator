package net.earelin.mercator.shared.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import net.earelin.mercator.shared.domain.source.CachedDocument;
import net.earelin.mercator.shared.domain.source.Representation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskDocumentCacheTest {

    @Test
    void roundTripsBodyRepresentationAndCharset(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        byte[] body = "COMPAÑÍA ESPAÑOLA".getBytes(StandardCharsets.ISO_8859_1);
        cache.put("BORME-A-2024-1-01", new CachedDocument(Representation.TXT, body, StandardCharsets.ISO_8859_1));

        Optional<CachedDocument> read = cache.get("BORME-A-2024-1-01");

        assertTrue(read.isPresent());
        assertEquals(Representation.TXT, read.get().representation());
        assertEquals(StandardCharsets.ISO_8859_1, read.get().charset());
        assertArrayEquals(body, read.get().body());
    }

    @Test
    void missReturnsEmpty(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        assertTrue(cache.get("BORME-A-2099-999-99").isEmpty());
    }

    @Test
    void putOverwritesExistingEntry(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("id", new CachedDocument(Representation.XML, "v1".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        cache.put("id", new CachedDocument(Representation.PDF, "v2".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        CachedDocument read = cache.get("id").orElseThrow();
        assertEquals(Representation.PDF, read.representation());
        assertArrayEquals("v2".getBytes(StandardCharsets.UTF_8), read.body());
    }
}
