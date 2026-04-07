/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HuggingFaceHub}. The tests that exercise actual
 * network I/O are gated behind a system property {@code smile.test.network}
 * so that they are skipped in offline CI environments.
 * Run with: {@code ./gradlew :base:test "-Psmile.test.network=true"}
 * or {@code sbt "-Dsmile.test.network=true" base/"testOnly smile.util.HuggingFaceHubTest"}
 *
 * @author Haifeng Li
 */
public class HuggingFaceHubTest {

    // -----------------------------------------------------------------------
    // Pure-logic tests (no network, no filesystem side effects)
    // -----------------------------------------------------------------------

    @Test
    public void testStripWeakEtag_null() {
        assertNull(HuggingFaceHub.stripWeakEtag(null));
    }

    @Test
    public void testStripWeakEtag_blank() {
        assertNull(HuggingFaceHub.stripWeakEtag("   "));
    }

    @Test
    public void testStripWeakEtag_plain() {
        assertEquals("abc123", HuggingFaceHub.stripWeakEtag("abc123"));
    }

    @Test
    public void testStripWeakEtag_quoted() {
        assertEquals("abc123", HuggingFaceHub.stripWeakEtag("\"abc123\""));
    }

    @Test
    public void testStripWeakEtag_weakQuoted() {
        // Python: W/"abc123" → abc123
        assertEquals("abc123", HuggingFaceHub.stripWeakEtag("W/\"abc123\""));
    }

    @Test
    public void testStripWeakEtag_weakUnquoted() {
        assertEquals("abc123", HuggingFaceHub.stripWeakEtag("W/abc123"));
    }

    @Test
    public void testIsCommitHash_valid() {
        assertTrue(HuggingFaceHub.isCommitHash("a" + "0".repeat(39)));
        assertTrue(HuggingFaceHub.isCommitHash("0".repeat(40)));
        assertTrue(HuggingFaceHub.isCommitHash("abcdef1234567890abcdef1234567890abcdef12"));
    }

    @Test
    public void testIsCommitHash_invalid() {
        assertFalse(HuggingFaceHub.isCommitHash("main"));
        assertFalse(HuggingFaceHub.isCommitHash("v1.0"));
        // 39 chars → too short
        assertFalse(HuggingFaceHub.isCommitHash("0".repeat(39)));
        // 41 chars → too long
        assertFalse(HuggingFaceHub.isCommitHash("0".repeat(41)));
        // uppercase → only lowercase hex is accepted
        assertFalse(HuggingFaceHub.isCommitHash("A".repeat(40)));
    }

    @Test
    public void testResolveEndpoint_default() {
        // Should return the default when HF_ENDPOINT is not set.
        String ep = HuggingFaceHub.resolveEndpoint();
        // In a clean test environment HF_ENDPOINT is unset → default.
        // We only assert it is non-blank and has no trailing slash.
        assertNotNull(ep);
        assertFalse(ep.isBlank());
        assertFalse(ep.endsWith("/"), "Endpoint must not end with a slash");
    }

    @Test
    public void testResolveCacheDir_explicitOverride() throws Exception {
        Path tmp = Files.createTempDirectory("smile-hf-test-cache-");
        try {
            Path result = HuggingFaceHub.resolveCacheDir(tmp);
            assertEquals(tmp, result);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testResolveCacheDir_default() {
        Path result = HuggingFaceHub.resolveCacheDir(null);
        assertNotNull(result);
        // Must be an absolute path ending with the expected suffix
        // (unless overridden by env vars in the test environment).
        assertTrue(result.isAbsolute());
    }

    // -----------------------------------------------------------------------
    // Cache-layout tests (use a temp directory, no real network)
    // -----------------------------------------------------------------------

    /**
     * Verifies that {@link HuggingFaceHub#tryLoadFromCache} returns empty
     * when the cache is completely empty.
     */
    @Test
    public void testTryLoadFromCache_emptyCache() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            Optional<Path> result = HuggingFaceHub.tryLoadFromCache(
                    "owner/model", "config.json",
                    HuggingFaceHub.RepoType.MODEL, "main", cacheDir);
            assertTrue(result.isEmpty());
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies that {@link HuggingFaceHub#tryLoadFromCache} finds a file
     * that was previously "cached" by manually constructing the expected
     * cache directory layout.
     */
    @Test
    public void testTryLoadFromCache_hit() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            // Reproduce the Python cache layout manually.
            String repoId = "owner/mymodel";
            String filename = "config.json";
            String commitHash = "a".repeat(40); // fake 40-char SHA

            Path repoCache = cacheDir.resolve("models--owner--mymodel");
            Path blobsDir = repoCache.resolve("blobs");
            Path snapshotsDir = repoCache.resolve("snapshots");
            Path refsDir = repoCache.resolve("refs");
            Files.createDirectories(blobsDir);
            Files.createDirectories(snapshotsDir.resolve(commitHash));
            Files.createDirectories(refsDir);

            // Write a fake blob.
            Path blobPath = blobsDir.resolve("sha256fake");
            Files.writeString(blobPath, "{\"model_type\": \"bert\"}", StandardCharsets.UTF_8);

            // Write the snapshot entry (as a plain file here; real library uses symlinks).
            Path snapshotFile = snapshotsDir.resolve(commitHash).resolve(filename);
            Files.writeString(snapshotFile, "{\"model_type\": \"bert\"}", StandardCharsets.UTF_8);

            // Write the refs file mapping "main" → commitHash.
            Files.writeString(refsDir.resolve("main"), commitHash, StandardCharsets.UTF_8);

            // tryLoadFromCache via branch name.
            Optional<Path> result = HuggingFaceHub.tryLoadFromCache(
                    repoId, filename, HuggingFaceHub.RepoType.MODEL, "main", cacheDir);
            assertTrue(result.isPresent(), "Expected cache hit for 'main' branch");
            assertEquals(snapshotFile, result.get());

            // tryLoadFromCache via direct commit hash.
            Optional<Path> result2 = HuggingFaceHub.tryLoadFromCache(
                    repoId, filename, HuggingFaceHub.RepoType.MODEL, commitHash, cacheDir);
            assertTrue(result2.isPresent(), "Expected cache hit for direct commit hash");
            assertEquals(snapshotFile, result2.get());

        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies that {@code localFilesOnly = true} throws {@link IOException}
     * when the file is absent from the cache.
     */
    @Test
    public void testDownload_localFilesOnly_throws() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            assertThrows(IOException.class, () ->
                    HuggingFaceHub.download("owner/model", "config.json",
                            HuggingFaceHub.RepoType.MODEL, "main",
                            null, cacheDir, false, true));
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies that invalid arguments throw {@link IllegalArgumentException}.
     */
    @Test
    public void testDownload_invalidArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> HuggingFaceHub.download(null, "config.json"));
        assertThrows(IllegalArgumentException.class,
                () -> HuggingFaceHub.download("", "config.json"));
        assertThrows(IllegalArgumentException.class,
                () -> HuggingFaceHub.download("owner/model", null));
        assertThrows(IllegalArgumentException.class,
                () -> HuggingFaceHub.download("owner/model", ""));
    }

    /**
     * Verifies the repo-cache directory name encoding matches Python's
     * {@code repo_id.replace("/", "--")} convention.
     */
    @Test
    public void testRepoCacheName() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            // localFilesOnly=true will throw but will have already created the
            // blobs/, snapshots/ and refs/ subdirectories.
            try {
                HuggingFaceHub.download("google/bert-base-uncased", "config.json",
                        HuggingFaceHub.RepoType.MODEL, "main",
                        null, cacheDir, false, true);
            } catch (IOException ignored) { /* expected */ }

            // The repo cache dir must be models--google--bert-base-uncased
            Path expected = cacheDir.resolve("models--google--bert-base-uncased");
            assertTrue(Files.exists(expected),
                    "Expected repo cache directory: " + expected);
            assertTrue(Files.exists(expected.resolve("blobs")));
            assertTrue(Files.exists(expected.resolve("snapshots")));
            assertTrue(Files.exists(expected.resolve("refs")));
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies the dataset repo type uses the correct URL-segment prefix.
     */
    @Test
    public void testRepoType_dataset() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            try {
                HuggingFaceHub.download("owner/mydata", "data.csv",
                        HuggingFaceHub.RepoType.DATASET, "main",
                        null, cacheDir, false, true);
            } catch (IOException ignored) { }

            Path expected = cacheDir.resolve("datasets--owner--mydata");
            assertTrue(Files.exists(expected),
                    "Dataset repo should use 'datasets--' prefix: " + expected);
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies the space repo type uses the correct URL-segment prefix.
     */
    @Test
    public void testRepoType_space() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            try {
                HuggingFaceHub.download("owner/myspace", "app.py",
                        HuggingFaceHub.RepoType.SPACE, "main",
                        null, cacheDir, false, true);
            } catch (IOException ignored) { }

            Path expected = cacheDir.resolve("spaces--owner--myspace");
            assertTrue(Files.exists(expected),
                    "Space repo should use 'spaces--' prefix: " + expected);
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Verifies that a subfolder is correctly merged into the filename.
     * The snapshot directory entry must use the combined path.
     */
    @Test
    public void testSubfolderMerge() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            // Pre-populate the cache with a nested file so localFilesOnly succeeds.
            String commitHash = "b".repeat(40);
            Path repoCache = cacheDir.resolve("models--owner--mymodel");
            Path snapshotsDir = repoCache.resolve("snapshots");
            Path refsDir = repoCache.resolve("refs");
            Path nestedDir = snapshotsDir.resolve(commitHash).resolve("subdir");
            Files.createDirectories(nestedDir);
            Files.createDirectories(refsDir);

            Path nestedFile = nestedDir.resolve("weights.bin");
            Files.write(nestedFile, new byte[]{1, 2, 3});

            Files.writeString(refsDir.resolve("main"), commitHash, StandardCharsets.UTF_8);

            Path result = HuggingFaceHub.download("owner/mymodel", "weights.bin",
                    HuggingFaceHub.RepoType.MODEL, "main",
                    "subdir", cacheDir, false, true);

            assertEquals(nestedFile, result);
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    // -----------------------------------------------------------------------
    // Network integration tests (skipped unless -Dsmile.test.network=true)
    // -----------------------------------------------------------------------

    /**
     * Downloads a tiny public file from Hugging Face Hub and validates
     * the cache layout on disk.
     *
     * <p>Skipped unless the Gradle project property {@code smile.test.network=true}
     * is set:
     * <pre>{@code ./gradlew :base:test -Psmile.test.network=true}</pre>
     */
    @Test
    @Tag("network")
    public void testDownload_publicFile() throws Exception {
        if (!Boolean.getBoolean("smile.test.network")) {
            System.out.println("Skipping network test (enable with -Psmile.test.network=true)");
            return;
        }

        // gpt2/config.json is a tiny (~665 byte) stable public file that
        // reliably contains "model_type": "gpt2".
        final String repoId   = "gpt2";
        final String filename = "config.json";

        Path cacheDir = Files.createTempDirectory("smile-hf-live-cache-");
        try {
            Path result = HuggingFaceHub.download(repoId, filename,
                    HuggingFaceHub.RepoType.MODEL, "main",
                    null, cacheDir, false, false);

            assertTrue(Files.exists(result), "Downloaded file must exist: " + result);
            assertTrue(Files.size(result) > 0, "Downloaded file must be non-empty");

            // Verify the cache layout: single-name repo → models--gpt2
            Path repoCache = cacheDir.resolve("models--gpt2");
            assertTrue(Files.exists(repoCache.resolve("blobs")),
                    "blobs/ directory must exist in repo cache");
            assertTrue(Files.exists(repoCache.resolve("snapshots")),
                    "snapshots/ directory must exist in repo cache");
            assertTrue(Files.exists(repoCache.resolve("refs")),
                    "refs/ directory must exist in repo cache");

            // A second call must return the same cached path without re-downloading.
            Path result2 = HuggingFaceHub.download(repoId, filename,
                    HuggingFaceHub.RepoType.MODEL, "main",
                    null, cacheDir, false, false);
            assertEquals(result, result2, "Second call must return the same cached path");

            // Verify the file content is gpt2's config with model_type field.
            String content = Files.readString(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("model_type"),
                    "config.json should contain 'model_type', got: "
                            + content.substring(0, Math.min(300, content.length())));

            System.out.println("Downloaded to: " + result);
            System.out.println("Content snippet: " + content.substring(0, Math.min(200, content.length())));

        } finally {
            deleteRecursively(cacheDir);
        }
    }

    /**
     * Tests {@link HuggingFaceHub#deleteRepoCache} removes the repo directory.
     */
    @Test
    public void testDeleteRepoCache() throws Exception {
        Path cacheDir = Files.createTempDirectory("smile-hf-cache-");
        try {
            // Create a fake repo cache directory.
            Path repoCache = cacheDir.resolve("models--owner--todel");
            Files.createDirectories(repoCache.resolve("blobs"));
            Files.createDirectories(repoCache.resolve("snapshots"));
            assertTrue(Files.exists(repoCache));

            HuggingFaceHub.deleteRepoCache("owner/todel", HuggingFaceHub.RepoType.MODEL, cacheDir);
            assertFalse(Files.exists(repoCache), "Repo cache should have been deleted");
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}

