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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility for downloading files from the
 * <a href="https://huggingface.co">Hugging Face Hub</a> with local disk caching.
 *
 * <p>This class reproduces the same on-disk cache layout as official Python
 * function {@code hf_hub_download} so that downloads performed by this class
 * are interoperable with the Python library and vice versa.
 *
 * <h2>Cache layout</h2>
 * <pre>
 * $HF_HOME/hub/
 *   models--{owner}--{repo}/
 *     blobs/
 *       {sha256}          ← actual file content
 *     snapshots/
 *       {commit_hash}/
 *         {filename}      ← relative symlink → ../../blobs/{sha256}
 *     refs/
 *       {revision}        ← text file containing the resolved commit hash
 * </pre>
 *
 * <h2>Environment variables</h2>
 * <ul>
 *   <li>{@code HF_HOME} – base directory for all Hugging Face data
 *       (default: {@code ~/.cache/huggingface}).</li>
 *   <li>{@code HUGGINGFACE_HUB_CACHE} – override cache root
 *       (default: {@code $HF_HOME/hub}).</li>
 *   <li>{@code HF_ENDPOINT} – Hugging Face endpoint
 *       (default: {@code https://huggingface.co}).</li>
 *   <li>{@code HF_TOKEN} – API token for private repositories
 *       (also read from {@code ~/.cache/huggingface/token}).</li>
 * </ul>
 *
 * <h2>Supported repo types</h2>
 * <ul>
 *   <li>{@code model} (default)</li>
 *   <li>{@code dataset}</li>
 *   <li>{@code space}</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public class HuggingFaceHub {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HuggingFaceHub.class);

    /** Default Hugging Face endpoint. */
    public static final String DEFAULT_ENDPOINT = "https://huggingface.co";

    /** Header name for the ETag (content hash). */
    private static final String HEADER_ETAG = "ETag";

    /** Header name for the resolved commit hash. */
    private static final String HEADER_COMMIT_HASH = "X-Repo-Commit";

    /** Header name for the linked blob SHA (when the file is an LFS pointer). */
    private static final String HEADER_LFS_SHA = "X-Linked-Etag";

    /** Header name for the linked blob size. */
    private static final String HEADER_LFS_SIZE = "X-Linked-Size";

    /** Prefix used in ETag headers to strip weak-validator markers. */
    private static final String ETAG_WEAK_PREFIX = "W/";

    /** Buffer size for streaming downloads (8 MiB). */
    private static final int DOWNLOAD_BUFFER = 8 * 1024 * 1024;

    /** Connection / read timeout in milliseconds (10 s). */
    private static final int TIMEOUT_MS = 10_000;

    // -----------------------------------------------------------------------
    // Repo type → URL segment mapping
    // -----------------------------------------------------------------------

    /** Supported Hugging Face repository types. */
    public enum RepoType {
        /** A model repository (default). */
        MODEL("models"),
        /** A dataset repository. */
        DATASET("datasets"),
        /** A Space repository. */
        SPACE("spaces");

        /** The URL path segment used in the Hugging Face API. */
        public final String urlSegment;

        RepoType(String urlSegment) {
            this.urlSegment = urlSegment;
        }

        /**
         * Returns the cache directory prefix for this repo type.
         * Models use {@code "models"}, datasets {@code "datasets"},
         * spaces {@code "spaces"}.
         *
         * @return the cache folder name prefix.
         */
        public String cachePrefix() {
            return urlSegment;
        }
    }

    // -----------------------------------------------------------------------
    // Private constructor – pure static utility class
    // -----------------------------------------------------------------------
    private HuggingFaceHub() { }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Downloads a single file from a Hugging Face Hub repository and caches it
     * locally.
     *
     * @param repoId   the repository identifier in {@code owner/name} format
     *                 (e.g. {@code "google/bert-base-uncased"}).
     * @param filename the path of the file inside the repository
     *                 (e.g. {@code "config.json"} or {@code "data/train.csv"}).
     * @return the local {@link Path} to the cached file.
     * @throws IOException if a network or filesystem error occurs.
     */
    public static Path download(String repoId, String filename) throws IOException {
        return download(repoId, filename, RepoType.MODEL, "main", null, null, false, false);
    }

    /**
     * Downloads a single file from a Hugging Face Hub repository and caches it
     * locally.
     *
     * @param repoId          the repository identifier ({@code "owner/name"}).
     * @param filename        the file path inside the repository.
     * @param repoType        the repository type ({@link RepoType#MODEL},
     *                        {@link RepoType#DATASET}, or {@link RepoType#SPACE}).
     * @param revision        the git revision to download from (branch, tag, or full
     *                        commit SHA). Defaults to {@code "main"}.
     * @param subfolder       an optional subdirectory prefix prepended to
     *                        {@code filename}. May be {@code null}.
     * @param cacheDir        override for the local cache root directory.
     *                        When {@code null}, the value of the
     *                        {@code HUGGINGFACE_HUB_CACHE} / {@code HF_HOME}
     *                        environment variables is used.
     * @param forceDownload   when {@code true}, bypass the local cache and
     *                        always re-download the file.
     * @param localFilesOnly  when {@code true}, raise an {@link IOException}
     *                        instead of making any network request if the file
     *                        is not already cached.
     * @return the local {@link Path} to the cached file.
     * @throws IOException if a network or filesystem error occurs, or if
     *                     {@code localFilesOnly} is {@code true} and the file
     *                     is not cached.
     */
    public static Path download(String repoId,
                                String filename,
                                RepoType repoType,
                                String revision,
                                String subfolder,
                                Path cacheDir,
                                boolean forceDownload,
                                boolean localFilesOnly) throws IOException {
        if (repoId == null || repoId.isBlank()) throw new IllegalArgumentException("repoId must not be blank");
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename must not be blank");
        if (repoType == null) repoType = RepoType.MODEL;
        if (revision == null || revision.isBlank()) revision = "main";

        // Merge subfolder into filename
        if (subfolder != null && !subfolder.isBlank()) {
            filename = subfolder.replace('\\', '/') + "/" + filename.replace('\\', '/');
        }
        // Capture as effectively final for use in lambdas below.
        final String resolvedFilename = filename;

        // ----------------------------------------------------------------
        // Resolve cache root
        // ----------------------------------------------------------------
        Path cache = resolveCacheDir(cacheDir);

        // ----------------------------------------------------------------
        // Build repo cache directory:
        //   {cache}/{prefix}--{owner}--{name}
        //   e.g. models--google--bert-base-uncased
        // ----------------------------------------------------------------
        String repoCacheName = repoType.cachePrefix() + "--" + repoId.replace("/", "--");
        Path repoCache = cache.resolve(repoCacheName);
        Path blobsDir     = repoCache.resolve("blobs");
        Path snapshotsDir = repoCache.resolve("snapshots");
        Path refsDir      = repoCache.resolve("refs");

        Files.createDirectories(blobsDir);
        Files.createDirectories(snapshotsDir);
        Files.createDirectories(refsDir);

        // ----------------------------------------------------------------
        // Resolve the revision to a commit hash using the refs/ cache or
        // the API (HEAD request).
        // ----------------------------------------------------------------
        String endpoint = resolveEndpoint();
        String token = resolveToken();

        // If revision looks like a full 40-char hex commit hash, use it directly.
        String commitHash = null;
        if (isCommitHash(revision)) {
            commitHash = revision;
        } else {
            // Check refs/ cache for a previously resolved commit hash.
            Path refFile = refsDir.resolve(revision.replace("/", "_"));
            if (!forceDownload && Files.exists(refFile)) {
                commitHash = Files.readString(refFile, StandardCharsets.UTF_8).strip();
                logger.debug("Resolved revision '{}' to commit '{}' from cache.", revision, commitHash);
            }
        }

        // ----------------------------------------------------------------
        // Build the URL for this file.
        // ----------------------------------------------------------------
        // REPO_TYPES_URL_PREFIXES:
        //   model   → ""            → {endpoint}/{repoId}/resolve/{revision}/{filename}
        //   dataset → "datasets/"   → {endpoint}/datasets/{repoId}/resolve/{revision}/{filename}
        //   space   → "spaces/"     → {endpoint}/spaces/{repoId}/resolve/{revision}/{filename}
        String encodedFilename = filename.replace(" ", "%20");
        String repoPrefix = repoType == RepoType.MODEL ? "" : repoType.urlSegment + "/";
        String fileUrl = endpoint + "/" + repoPrefix + repoId + "/resolve/"
                + revision + "/" + encodedFilename;

        // ----------------------------------------------------------------
        // Fast path: if we already have the commit hash and the snapshot
        // symlink exists and we are not forced, return the cached path.
        // ----------------------------------------------------------------
        if (!forceDownload && commitHash != null) {
            Path snapshotFile = snapshotsDir.resolve(commitHash).resolve(filename);
            if (Files.exists(snapshotFile)) {
                logger.debug("Cache hit: {}", snapshotFile);
                return snapshotFile;
            }
        }

        // ----------------------------------------------------------------
        // Network unavailable / local-only mode
        // ----------------------------------------------------------------
        if (localFilesOnly) {
            // Try to find any existing snapshot for this file.
            if (commitHash != null) {
                Path snapshotFile = snapshotsDir.resolve(commitHash).resolve(filename);
                if (Files.exists(snapshotFile)) return snapshotFile;
            }
            // Scan all known snapshots.
            if (Files.exists(snapshotsDir)) {
                try (var stream = Files.list(snapshotsDir)) {
                    var found = stream
                            .map(s -> s.resolve(resolvedFilename))
                            .filter(Files::exists)
                            .findFirst();
                    if (found.isPresent()) return found.get();
                }
            }
            throw new IOException("File '%s' is not in the local cache and localFilesOnly=true.".formatted(resolvedFilename));
        }

        // ================================================================
        // Network path: HEAD request to resolve ETag / commit hash
        // ================================================================
        HttpURLConnection headConn = openConnection(fileUrl, token);
        headConn.setRequestMethod("HEAD");
        headConn.setConnectTimeout(TIMEOUT_MS);
        headConn.setReadTimeout(TIMEOUT_MS);
        headConn.setInstanceFollowRedirects(true);

        // Send ETag of existing blob for conditional requests.
        if (!forceDownload && commitHash != null) {
            Path snapshotFile = snapshotsDir.resolve(commitHash).resolve(filename);
            if (Files.isSymbolicLink(snapshotFile)) {
                Path blobPath = snapshotFile.toRealPath();
                String existingEtag = blobPath.getFileName().toString();
                headConn.setRequestProperty("If-None-Match", "\"" + existingEtag + "\"");
            }
        }

        int headStatus;
        try {
            headConn.connect();
            headStatus = headConn.getResponseCode();
        } catch (IOException e) {
            // Network error — fall back to cache if available.
            logger.warn("HEAD request failed for {}: {}", fileUrl, e.getMessage());
            if (commitHash != null) {
                Path snapshotFile = snapshotsDir.resolve(commitHash).resolve(filename);
                if (Files.exists(snapshotFile)) {
                    logger.info("Falling back to cached file: {}", snapshotFile);
                    return snapshotFile;
                }
            }
            throw new IOException("Network error and no cached copy found for: " + fileUrl, e);
        } finally {
            headConn.disconnect();
        }

        if (headStatus == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new IOException("401 Unauthorized for '%s'. Provide a valid HF_TOKEN for private repos.".formatted(fileUrl));
        }
        if (headStatus == HttpURLConnection.HTTP_FORBIDDEN) {
            throw new IOException("403 Forbidden for '%s'. You may not have access to this repository.".formatted(fileUrl));
        }
        if (headStatus == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new FileNotFoundException("404 Not Found: '%s' in repo '%s' at revision '%s'.".formatted(filename, repoId, revision));
        }
        if (headStatus != HttpURLConnection.HTTP_OK && headStatus != HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new IOException("Unexpected HTTP %d for HEAD request on: %s".formatted(headStatus, fileUrl));
        }

        // Extract metadata from response headers.
        String etag = stripWeakEtag(headConn.getHeaderField(HEADER_ETAG));
        // For LFS files, X-Linked-Etag holds the Git SHA-1 of the LFS blob object.
        // Prefer it over the regular ETag as it is more stable across redirects.
        // IMPORTANT: both are Git SHA-1 identifiers (40 hex chars), NOT SHA-256
        // content hashes — never compare them against a computed SHA-256.
        String lfsEtag = stripWeakEtag(headConn.getHeaderField(HEADER_LFS_SHA));
        String serverCommitHash = headConn.getHeaderField(HEADER_COMMIT_HASH);
        String lfsSizeStr = headConn.getHeaderField(HEADER_LFS_SIZE);

        // Use the LFS ETag as the blob cache key, falling back to the regular ETag.
        String blobSha = lfsEtag != null ? lfsEtag : etag;

        // Update the commit hash from the server response.
        if (serverCommitHash != null && !serverCommitHash.isBlank()) {
            commitHash = serverCommitHash.strip();
            // Cache the revision → commit mapping.
            if (!isCommitHash(revision)) {
                Path refFile = refsDir.resolve(revision.replace("/", "_"));
                writeFileAtomically(refFile, commitHash);
            }
        }

        if (commitHash == null) {
            // Fall back: use the revision itself as a pseudo-commit.
            commitHash = revision;
        }

        Path snapshotDir  = snapshotsDir.resolve(commitHash);
        Path snapshotFile = snapshotDir.resolve(filename);
        Files.createDirectories(snapshotDir);
        // Create parent directories for nested filenames (e.g. "data/train.csv")
        if (snapshotFile.getParent() != null) {
            Files.createDirectories(snapshotFile.getParent());
        }

        // ----------------------------------------------------------------
        // 304 Not Modified: cached blob is still valid
        // ----------------------------------------------------------------
        if (headStatus == HttpURLConnection.HTTP_NOT_MODIFIED && Files.exists(snapshotFile)) {
            logger.debug("304 Not Modified — using cached: {}", snapshotFile);
            return snapshotFile;
        }

        // ----------------------------------------------------------------
        // Check whether the blob is already in the blobs/ directory.
        // ----------------------------------------------------------------
        Path blobPath = blobSha != null ? blobsDir.resolve(blobSha) : null;
        if (!forceDownload && blobPath != null && Files.exists(blobPath)) {
            // Blob exists; just (re-)create the snapshot symlink.
            createOrUpdateSymlink(snapshotFile, blobPath, blobsDir, snapshotDir);
            logger.debug("Blob cache hit: {}", blobPath);
            return snapshotFile;
        }

        // ================================================================
        // Full download needed
        // ================================================================
        logger.info("Downloading {} from {}", filename, fileUrl);

        // Download to a temp file first; atomic rename afterward.
        Path tempFile = blobsDir.resolve("." + UUID.randomUUID() + ".tmp");
        try {
            HttpURLConnection getConn = openConnection(fileUrl, token);
            getConn.setRequestMethod("GET");
            getConn.setConnectTimeout(TIMEOUT_MS);
            getConn.setReadTimeout(0); // unlimited for body transfer
            getConn.setInstanceFollowRedirects(true);

            int getStatus;
            try {
                getConn.connect();
                getStatus = getConn.getResponseCode();
            } catch (IOException e) {
                throw new IOException("GET request failed for: " + fileUrl, e);
            }

            if (getStatus != HttpURLConnection.HTTP_OK) {
                getConn.disconnect();
                throw new IOException("Unexpected HTTP %d for GET: %s".formatted(getStatus, fileUrl));
            }

            // Stream body → temp file, computing SHA-256 on the fly.
            // The SHA-256 is used as the blob key when no ETag is available.
            // NOTE: The ETag / X-Linked-Etag headers from the HF Hub are Git SHA-1
            // object identifiers (40 hex chars), NOT SHA-256 content hashes — so we
            // must never compare them against the computed SHA-256.
            String downloadedSha256 = streamToFile(getConn.getInputStream(), tempFile, lfsSizeStr);
            getConn.disconnect();

            // Determine the blob key:
            //   - If the server gave us an ETag (Git SHA-1), use it as-is as the key.
            //   - Otherwise fall back to the SHA-256 of the downloaded content.
            if (blobSha == null) {
                blobSha = downloadedSha256;
                blobPath = blobsDir.resolve(blobSha);
            }
            // (blobPath was already set above when blobSha was non-null)

            // Atomically move temp file to final blob path.
            Files.move(tempFile, blobPath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Saved blob: {}", blobPath);

        } catch (IOException e) {
            // Clean up temp file on failure.
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) { }
            throw e;
        }

        // Create / update snapshot symlink → blob.
        createOrUpdateSymlink(snapshotFile, blobPath, blobsDir, snapshotDir);
        return snapshotFile;
    }

    // =========================================================================
    //  Helper methods
    // =========================================================================

    /**
     * Resolves the local cache root directory, respecting environment variables:
     * <ol>
     *   <li>{@code cacheDir} argument (if non-null)</li>
     *   <li>{@code HUGGINGFACE_HUB_CACHE} environment variable</li>
     *   <li>{@code HF_HOME} environment variable + {@code "/hub"}</li>
     *   <li>{@code ~/.cache/huggingface/hub}</li>
     * </ol>
     *
     * @param cacheDir explicit override; may be {@code null}.
     * @return the resolved cache root path.
     */
    public static Path resolveCacheDir(Path cacheDir) {
        if (cacheDir != null) return cacheDir;

        String env = System.getenv("HUGGINGFACE_HUB_CACHE");
        if (env != null && !env.isBlank()) return Path.of(env);

        String hfHome = System.getenv("HF_HOME");
        if (hfHome != null && !hfHome.isBlank()) return Path.of(hfHome, "hub");

        return Path.of(System.getProperty("user.home"), ".cache", "huggingface", "hub");
    }

    /**
     * Returns the Hugging Face Hub API endpoint, from the {@code HF_ENDPOINT}
     * environment variable, falling back to {@link #DEFAULT_ENDPOINT}.
     *
     * @return the endpoint URL (no trailing slash).
     */
    public static String resolveEndpoint() {
        String env = System.getenv("HF_ENDPOINT");
        if (env != null && !env.isBlank()) return env.stripTrailing().replaceAll("/+$", "");
        return DEFAULT_ENDPOINT;
    }

    /**
     * Returns the API token to use for authenticated requests, checking in
     * priority order:
     * <ol>
     *   <li>{@code HF_TOKEN} environment variable</li>
     *   <li>{@code HUGGING_FACE_HUB_TOKEN} environment variable (legacy)</li>
     *   <li>{@code ~/.cache/huggingface/token} file (written by {@code huggingface-cli login})</li>
     * </ol>
     *
     * @return the token string, or {@code null} if none is configured.
     */
    public static String resolveToken() {
        String token = System.getenv("HF_TOKEN");
        if (token != null && !token.isBlank()) return token.strip();

        token = System.getenv("HUGGING_FACE_HUB_TOKEN");
        if (token != null && !token.isBlank()) return token.strip();

        // Check the token file written by `huggingface-cli login`.
        Path tokenFile = Path.of(System.getProperty("user.home"), ".cache", "huggingface", "token");
        if (Files.exists(tokenFile)) {
            try {
                token = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
                if (!token.isBlank()) return token;
            } catch (IOException e) {
                logger.warn("Could not read token file: {}", tokenFile);
            }
        }
        return null;
    }

    /**
     * Opens an {@link HttpURLConnection} to the given URL with the
     * Authorization header set if a token is provided.
     *
     * @param url   the URL string.
     * @param token the Bearer token, or {@code null}.
     * @return an open (not yet connected) connection.
     * @throws IOException if the URL is malformed or the connection cannot be opened.
     */
    private static HttpURLConnection openConnection(String url, String token) throws IOException {
        var conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "smile/HuggingFaceHub");
        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        return conn;
    }

    /**
     * Strips the weak validator prefix ({@code W/}) and surrounding quotes
     * from an ETag header value.
     *
     * <p>Example: {@code W/"abc123"} → {@code abc123}.
     *
     * @param etag the raw ETag header value; may be {@code null}.
     * @return the normalised ETag string, or {@code null} if the input was null/blank.
     */
    static String stripWeakEtag(String etag) {
        if (etag == null || etag.isBlank()) return null;
        etag = etag.strip();
        if (etag.startsWith(ETAG_WEAK_PREFIX)) etag = etag.substring(ETAG_WEAK_PREFIX.length());
        if (etag.startsWith("\"") && etag.endsWith("\"")) etag = etag.substring(1, etag.length() - 1);
        return etag.isBlank() ? null : etag;
    }

    /**
     * Returns {@code true} if {@code revision} looks like a full 40-character
     * hexadecimal git commit SHA.
     *
     * @param revision the revision string to test.
     * @return {@code true} if it is a commit hash.
     */
    static boolean isCommitHash(String revision) {
        return revision != null && revision.matches("[0-9a-f]{40}");
    }

    /**
     * Streams the contents of {@code in} to {@code dest}, computing the
     * SHA-256 hash of the bytes written.
     *
     * <p>The returned SHA-256 is used as a blob cache key only when the server
     * did not supply an ETag / {@code X-Linked-Etag} header.  It is <em>not</em>
     * compared against those headers, which carry Git SHA-1 object identifiers
     * rather than SHA-256 content hashes.
     *
     * <p>If {@code expectedSizeStr} is non-null it is used only for progress
     * logging; the download always runs to completion regardless.
     *
     * @param in              the source input stream (closed when done).
     * @param dest            the destination file path (will be created/overwritten).
     * @param expectedSizeStr the expected size as a decimal string (may be null).
     * @return the hex-encoded SHA-256 of the bytes written.
     * @throws IOException if an I/O error occurs.
     */
    private static String streamToFile(InputStream in, Path dest, String expectedSizeStr) throws IOException {
        long expectedBytes = -1;
        if (expectedSizeStr != null) {
            try { expectedBytes = Long.parseLong(expectedSizeStr.strip()); }
            catch (NumberFormatException ignored) { }
        }

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        byte[] buffer = new byte[DOWNLOAD_BUFFER];
        long totalBytes = 0;
        try (in; var out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                sha256.update(buffer, 0, read);
                totalBytes += read;
                if (expectedBytes > 0) {
                    var progress = String.format("Downloaded %d / %d bytes (%.1f%%)",
                            totalBytes, expectedBytes, 100.0 * totalBytes / expectedBytes);
                    logger.debug(progress);
                }
            }
        }

        return HexFormat.of().formatHex(sha256.digest());
    }

    /**
     * Creates or replaces a relative symbolic link at {@code linkPath} pointing
     * to the blob file at {@code blobPath}.
     *
     * <p>The link target is expressed as a <em>relative</em> path from
     * {@code linkPath}'s parent to {@code blobPath}.
     *
     * <p>On platforms that do not support symbolic links (e.g. Windows without
     * Developer Mode or Administrator privileges), a hard link is attempted first;
     * if that also fails, a plain file copy is performed as a last resort so that
     * the download always succeeds.
     *
     * @param linkPath    the path at which the symlink (or copy) should appear.
     * @param blobPath    the actual blob file to link to.
     * @param blobsDir    the {@code blobs/} directory (used to compute the relative path).
     * @param snapshotDir the {@code snapshots/{commit}/} directory (parent of the link).
     * @throws IOException if the filesystem operation fails completely.
     */
    private static void createOrUpdateSymlink(Path linkPath, Path blobPath,
                                              Path blobsDir, Path snapshotDir) throws IOException {
        // Compute relative path from the symlink's directory to the blob.
        Path relTarget = snapshotDir.relativize(blobPath);

        // Delete a stale link / file first.
        Files.deleteIfExists(linkPath);

        try {
            Files.createSymbolicLink(linkPath, relTarget);
        } catch (UnsupportedOperationException | IOException e) {
            // Symbolic links not supported (common on Windows without privileges).
            logger.warn("Cannot create symlink {} -> {}: {}. Trying hard link.",
                    linkPath, relTarget, e.getMessage());
            try {
                Files.createLink(linkPath, blobPath);
            } catch (IOException ex) {
                logger.warn("Cannot create hard link. Falling back to file copy: {}", ex.getMessage());
                Files.copy(blobPath, linkPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Writes {@code content} to {@code path} atomically by first writing to a
     * sibling temp file and then renaming.
     *
     * @param path    the target file path.
     * @param content the text content to write (UTF-8 encoded).
     * @throws IOException if an I/O error occurs.
     */
    private static void writeFileAtomically(Path path, String content) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw e;
        }
    }

    /**
     * Convenience overload that uses {@link RepoType#MODEL} and {@code "main"} revision,
     * and accepts an explicit {@code token} argument instead of reading environment variables.
     *
     * @param repoId   the repository identifier ({@code "owner/name"}).
     * @param filename the file path inside the repository.
     * @param token    the Bearer token for private repositories, or {@code null}.
     * @return the local {@link Path} to the cached file.
     * @throws IOException if a network or filesystem error occurs.
     */
    public static Path download(String repoId, String filename, String token) throws IOException {
        return download(repoId, filename, RepoType.MODEL, "main", null, null, false, false);
    }

    /**
     * Returns the expected local path for a cached file <em>without</em> making
     * any network request. Returns an empty {@link java.util.Optional} if the
     * file is not currently cached.
     *
     * @param repoId   the repository identifier ({@code "owner/name"}).
     * @param filename the file path inside the repository.
     * @param repoType the repository type.
     * @param revision the git revision (branch, tag, or commit SHA).
     * @param cacheDir explicit cache root override; {@code null} to use the default.
     * @return an {@link java.util.Optional} containing the cached path, or empty.
     */
    public static java.util.Optional<Path> tryLoadFromCache(String repoId,
                                                             String filename,
                                                             RepoType repoType,
                                                             String revision,
                                                             Path cacheDir) {
        if (repoId == null || filename == null) return java.util.Optional.empty();
        if (repoType == null) repoType = RepoType.MODEL;
        if (revision == null) revision = "main";

        Path cache = resolveCacheDir(cacheDir);
        String repoCacheName = repoType.cachePrefix() + "--" + repoId.replace("/", "--");
        Path repoCache = cache.resolve(repoCacheName);
        Path snapshotsDir = repoCache.resolve("snapshots");
        Path refsDir = repoCache.resolve("refs");

        // If revision is a commit hash, look it up directly.
        if (isCommitHash(revision)) {
            Path p = snapshotsDir.resolve(revision).resolve(filename);
            return Files.exists(p) ? java.util.Optional.of(p) : java.util.Optional.empty();
        }

        // Resolve branch/tag → commit via the refs/ cache.
        Path refFile = refsDir.resolve(revision.replace("/", "_"));
        if (!Files.exists(refFile)) return java.util.Optional.empty();

        try {
            String commitHash = Files.readString(refFile, StandardCharsets.UTF_8).strip();
            Path p = snapshotsDir.resolve(commitHash).resolve(filename);
            return Files.exists(p) ? java.util.Optional.of(p) : java.util.Optional.empty();
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Deletes all cached data for a given repository.
     *
     * <p>This removes the entire repo cache directory, including all blobs,
     * snapshots, and refs. Use with care.
     *
     * @param repoId   the repository identifier ({@code "owner/name"}).
     * @param repoType the repository type.
     * @param cacheDir explicit cache root override; {@code null} to use the default.
     * @throws IOException if a filesystem error occurs.
     */
    public static void deleteRepoCache(String repoId, RepoType repoType, Path cacheDir) throws IOException {
        if (repoType == null) repoType = RepoType.MODEL;
        Path cache = resolveCacheDir(cacheDir);
        String repoCacheName = repoType.cachePrefix() + "--" + repoId.replace("/", "--");
        Path repoCache = cache.resolve(repoCacheName);
        if (Files.exists(repoCache)) {
            deleteRecursively(repoCache);
            logger.info("Deleted repo cache: {}", repoCache);
        }
    }

    /**
     * Recursively deletes a directory tree.
     */
    private static void deleteRecursively(Path root) throws IOException {
        var visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(root, visitor);
    }
}

