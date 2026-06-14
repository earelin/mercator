package net.earelin.mercator.shared.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import net.earelin.mercator.shared.domain.source.CachedDocument;
import net.earelin.mercator.shared.domain.source.Representation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskDocumentCacheTest {

    @Test
    void round_trips_body_representation_and_charset(@TempDir Path dir) {
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
    void shards_entries_into_folders_by_id_parts(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        // All-but-last id parts become nested folders; the full id is the leaf filename.
        Path leaf = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1");
        assertTrue(Files.isRegularFile(leaf.resolve("BORME-A-2024-1-01.body")));
        assertTrue(Files.isRegularFile(leaf.resolve("BORME-A-2024-1-01.meta")));
        // Not dumped flat in the cache root.
        assertFalse(Files.exists(dir.resolve("BORME-A-2024-1-01.body")));
    }

    @Test
    void miss_returns_empty(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        assertTrue(cache.get("BORME-A-2099-999-99").isEmpty());
    }

    @Test
    void truncated_meta_is_treated_as_a_miss(@TempDir Path dir) throws Exception {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        Path meta = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1").resolve("BORME-A-2024-1-01.meta");
        Files.writeString(meta, "XML\n"); // charset line missing

        assertTrue(cache.get("BORME-A-2024-1-01").isEmpty());
    }

    @Test
    void unparseable_meta_is_treated_as_a_miss(@TempDir Path dir) throws Exception {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        Path meta = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1").resolve("BORME-A-2024-1-01.meta");
        Files.writeString(meta, "NOT_A_REPRESENTATION\nUTF-8\n");

        assertTrue(cache.get("BORME-A-2024-1-01").isEmpty());
    }

    @Test
    void put_overwrites_existing_entry(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("id", new CachedDocument(Representation.XML, "v1".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        cache.put("id", new CachedDocument(Representation.PDF, "v2".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        CachedDocument read = cache.get("id").orElseThrow();
        assertEquals(Representation.PDF, read.representation());
        assertArrayEquals("v2".getBytes(StandardCharsets.UTF_8), read.body());
    }
}
