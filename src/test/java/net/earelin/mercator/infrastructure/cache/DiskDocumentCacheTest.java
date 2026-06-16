package net.earelin.mercator.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.earelin.mercator.domain.source.CachedDocument;
import net.earelin.mercator.domain.source.Representation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskDocumentCacheTest {

    @Test
    void round_trips_body_representation_and_charset(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        byte[] body = "COMPAÑÍA ESPAÑOLA".getBytes(StandardCharsets.ISO_8859_1);
        cache.put("BORME-A-2024-1-01", new CachedDocument(Representation.TXT, body, StandardCharsets.ISO_8859_1));

        Optional<CachedDocument> read = cache.get("BORME-A-2024-1-01");

        assertThat(read).isPresent();
        assertThat(read.get().representation()).isEqualTo(Representation.TXT);
        assertThat(read.get().charset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(read.get().body()).isEqualTo(body);
    }

    @Test
    void shards_entries_into_folders_by_id_parts(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        // All-but-last id parts become nested folders; the full id is the leaf filename.
        Path leaf = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1");
        assertThat(leaf.resolve("BORME-A-2024-1-01.body")).isRegularFile();
        assertThat(leaf.resolve("BORME-A-2024-1-01.meta")).isRegularFile();
        // Not dumped flat in the cache root.
        assertThat(dir.resolve("BORME-A-2024-1-01.body")).doesNotExist();
    }

    @Test
    void miss_returns_empty(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        assertThat(cache.get("BORME-A-2099-999-99")).isEmpty();
    }

    @Test
    void truncated_meta_is_treated_as_a_miss(@TempDir Path dir) throws Exception {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        Path meta = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1").resolve("BORME-A-2024-1-01.meta");
        Files.writeString(meta, "XML\n"); // charset line missing

        assertThat(cache.get("BORME-A-2024-1-01")).isEmpty();
    }

    @Test
    void unparseable_meta_is_treated_as_a_miss(@TempDir Path dir) throws Exception {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "x".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        Path meta = dir.resolve("BORME").resolve("A").resolve("2024").resolve("1").resolve("BORME-A-2024-1-01.meta");
        Files.writeString(meta, "NOT_A_REPRESENTATION\nUTF-8\n");

        assertThat(cache.get("BORME-A-2024-1-01")).isEmpty();
    }

    @Test
    void put_overwrites_existing_entry(@TempDir Path dir) {
        DiskDocumentCache cache = new DiskDocumentCache(dir);
        cache.put("id", new CachedDocument(Representation.XML, "v1".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        cache.put("id", new CachedDocument(Representation.PDF, "v2".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        CachedDocument read = cache.get("id").orElseThrow();
        assertThat(read.representation()).isEqualTo(Representation.PDF);
        assertThat(read.body()).isEqualTo("v2".getBytes(StandardCharsets.UTF_8));
    }
}
