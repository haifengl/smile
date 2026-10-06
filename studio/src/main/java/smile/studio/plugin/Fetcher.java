/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import smile.util.OS;
import smile.util.Strings;

/**
 * Fetches remote plugin and marketplace sources into the local cache, and resolves
 * local sources in place.
 *
 * <p>Remote fetches need {@code git} (for {@code github}/{@code git}/{@code git-subdir})
 * or plain HTTP (for {@code archive} and a hosted {@code marketplace.json}). Studio is
 * a desktop app and may run where {@code git} is absent; rather than bundle a JGit
 * implementation, a missing {@code git} degrades to a clear error and local sources
 * keep working (constraint C8).
 *
 * <p>Every remote fetch is bounded by a timeout and retried a small number of times
 * with backoff, per the NFR design. Nothing here executes plugin content: a fetch is
 * either a file copy or a network transfer (ADR-008).
 *
 * @author Haifeng Li
 */
final class Fetcher {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Fetcher.class);
    /** Total timeout for one network fetch. */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    /** Connect timeout for HTTP fetches. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** Number of attempts for a transient failure. */
    private static final int RETRIES = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Resolves a marketplace source to a local directory that holds
     * {@code .claude-plugin/marketplace.json}.
     *
     * @param source the marketplace source.
     * @param cacheDir the directory remote checkouts are cached under.
     * @return the local marketplace root.
     * @throws IOException if the source cannot be resolved.
     */
    Path marketplaceRoot(MarketplaceSource source, Path cacheDir) throws IOException {
        return switch (source) {
            case MarketplaceSource.Local local -> localRoot(local.path());
            case MarketplaceSource.Github github -> gitCheckout(
                    "https://github.com/" + github.repo() + ".git", github.ref(), null, cacheDir);
            case MarketplaceSource.Git git -> gitCheckout(git.url(), git.ref(), null, cacheDir);
            case MarketplaceSource.Hosted hosted -> hostedMarketplace(hosted.url(), cacheDir);
        };
    }

    /**
     * Resolves a plugin source to a local directory.
     *
     * @param source the plugin source.
     * @param marketplaceRoot the marketplace root, for a relative path.
     * @param cacheDir the directory remote checkouts are cached under.
     * @return the local plugin directory.
     * @throws IOException if the source cannot be resolved.
     */
    Path pluginRoot(PluginSource source, Path marketplaceRoot, Path cacheDir) throws IOException {
        return switch (source) {
            case PluginSource.RelativePath relative -> {
                Path resolved = PathGuard.resolve(marketplaceRoot, relative.path());
                if (!Files.isDirectory(resolved) && !Files.isRegularFile(resolved)) {
                    throw new IOException("Plugin path does not exist: " + relative.path());
                }
                yield resolved;
            }
            case PluginSource.Archive archive -> {
                Path target = cacheDir.resolve("archive").resolve(hash(archive.url()));
                deleteRecursively(target);
                downloadArchive(archive.url(), archive.sha256(), target);
                yield target;
            }
            case PluginSource.Github github -> gitCheckout(
                    "https://github.com/" + github.repo() + ".git", github.ref(), github.sha(), cacheDir);
            case PluginSource.Url url -> gitCheckout(url.url(), url.ref(), url.sha(), cacheDir);
            case PluginSource.GitSubdir subdir -> {
                Path checkout = gitCheckout(subdir.url(), subdir.ref(), subdir.sha(), cacheDir);
                Path sub = PathGuard.resolve(checkout, subdir.path());
                if (!Files.isDirectory(sub)) {
                    throw new IOException("git-subdir path does not exist: " + subdir.path());
                }
                yield sub;
            }
            case PluginSource.Npm npm -> throw new IOException(
                    "npm plugin sources are not supported yet: " + npm.packageName());
            case PluginSource.Command command -> throw new SecurityException(
                    "The 'command' source type is refused (ADR-008)");
        };
    }

    /**
     * A local source is either the manifest file itself or the directory that holds
     * {@code .claude-plugin/marketplace.json}.
     */
    private Path localRoot(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.isRegularFile(absolute)) {
            Path dir = absolute.getParent();
            if (dir == null) {
                throw new IOException("Cannot determine marketplace root for " + path);
            }
            // A file at <root>/.claude-plugin/marketplace.json means root is two up.
            if (dir.getFileName() != null && dir.getFileName().toString().equals(".claude-plugin")) {
                dir = dir.getParent();
            }
            return dir == null ? absolute.getParent() : dir;
        }
        if (Files.isDirectory(absolute)) {
            return absolute;
        }
        throw new IOException("Marketplace path does not exist: " + path);
    }

    /**
     * Clones a git repository into the cache and returns the checkout directory.
     *
     * <p>When {@code sha} is present it pins an exact 40-character commit, which is a
     * source of truth independent of the branch or tag named by {@code ref}: the
     * commit stays fetchable even after the ref is deleted or moved upstream. Git
     * cannot check out a raw sha in a shallow {@code clone --branch}, so the sha is
     * fetched in two steps — {@code init + remote add + fetch} (shallow if possible)
     * then {@code checkout FETCH_HEAD}. The {@code ref} is used only as a fallback
     * fetch hint when a shallow fetch of the sha is refused.
     *
     * @param url the clone URL.
     * @param ref the branch or tag, or null.
     * @param sha the pinned commit, or null to use {@code ref}.
     * @param cacheDir the cache root.
     * @return the checkout directory.
     */
    private Path gitCheckout(String url, String ref, String sha, Path cacheDir) throws IOException {
        requireGit();
        Path target = cacheDir.resolve("git").resolve(hash(url + "@" + (sha != null ? sha : ref)));
        Files.createDirectories(target.getParent());

        boolean pinned = sha != null && !sha.isBlank();
        if (Files.isDirectory(target.resolve(".git"))) {
            // A pinned checkout is immutable; a ref checkout may have moved, so only
            // the pinned form is reused as-is.
            if (pinned) {
                logger.debug("Reusing cached pinned git checkout {}", target);
                return target;
            }
        }
        deleteRecursively(target);
        Files.createDirectories(target);

        if (pinned) {
            checkoutSha(url, ref, sha, target);
        } else {
            cloneRef(url, ref, target);
        }
        return target;
    }

    /** Checks out a branch or tag, trying a shallow clone first then a full one. */
    private void cloneRef(String url, String ref, Path target) throws IOException {
        try {
            shallowClone(url, ref, target);
        } catch (IOException shallow) {
            // A local path or a host that refuses shallow fetches fails here; retry
            // with a full clone rather than giving up.
            logger.debug("Shallow clone failed ({}); retrying full", shallow.getMessage());
            Fetcher.deleteRecursively(target);
            Files.createDirectories(target);
            List<String> command = new java.util.ArrayList<>();
            command.add("git");
            command.add("clone");
            if (!Strings.isNullOrBlank(ref)) {
                command.add("--branch");
                command.add(ref);
            }
            command.add(url);
            command.add(target.toString());
            run(command, target.getParent());
        }
    }

    /** A shallow single-branch clone. */
    private void shallowClone(String url, String ref, Path target) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("clone");
        command.add("--depth");
        command.add("1");
        if (!Strings.isNullOrBlank(ref)) {
            command.add("--branch");
            command.add(ref);
        }
        command.add(url);
        command.add(target.toString());
        run(command, target.getParent());
    }

    /**
     * Fetches and checks out one exact commit. Direct fetch of an arbitrary sha is
     * not always allowed by a server, so this fetches the sha when possible and
     * falls back to a full fetch of the ref before checking out.
     */
    private void checkoutSha(String url, String ref, String sha, Path target) throws IOException {
        run(List.of("git", "init", "-q"), target);
        run(List.of("git", "remote", "add", "origin", url), target);

        // Preferred: fetch the exact commit directly (a shallow, single-commit fetch).
        try {
            run(List.of("git", "fetch", "--depth", "1", "origin", sha), target);
        } catch (IOException direct) {
            logger.debug("Direct fetch of sha {} failed ({}); fetching the full history",
                    sha, direct.getMessage());
            // Fetch without a depth so the pinned commit's objects are present even
            // when it is not the tip of the named ref.
            if (Strings.isNullOrBlank(ref)) {
                run(List.of("git", "fetch", "origin"), target);
            } else {
                run(List.of("git", "fetch", "origin", ref), target);
            }
        }
        run(List.of("git", "checkout", "-q", sha), target);
    }

    /**
     * Downloads a hosted {@code marketplace.json} into the cache.
     */
    private Path hostedMarketplace(String url, Path cacheDir) throws IOException {
        Path target = cacheDir.resolve("hosted").resolve(hash(url));
        Files.createDirectories(target);
        Path manifest = target.resolve(MarketplaceManifest.MARKETPLACE_FILE);
        Files.createDirectories(manifest.getParent());
        download(URI.create(url), manifest);
        return target;
    }

    /**
     * Downloads a plugin archive and extracts it under the destination, refusing
     * any entry that would escape {@code destination} (zip-slip defense).
     *
     * @param url the archive URL.
     * @param expectedSha256 the pinned digest, or null to skip the check.
     * @param destination the extraction directory.
     * @throws IOException if the download, verification, or extraction fails.
     */
    void downloadArchive(String url, String expectedSha256, Path destination) throws IOException {
        Files.createDirectories(destination);
        Path archive = Files.createTempFile("smile-plugin-", ".zip");
        try {
            download(URI.create(url), archive);
            if (!Sha256Verifier.verify(archive, expectedSha256)) {
                throw new IOException("Archive SHA-256 does not match the pinned digest: " + url);
            }
            extractZip(archive, destination);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    /**
     * Streams a URL to a file with retries.
     */
    private void download(URI uri, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
                HttpResponse<Path> response = http.send(request,
                        HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + uri);
                }
                return;
            } catch (IOException ex) {
                last = ex;
                backoff(attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + uri, ex);
            }
        }
        throw last != null ? last : new IOException("Failed to download " + uri);
    }

    /**
     * Extracts a zip, guarding each entry against path traversal.
     */
    private void extractZip(Path archive, Path destination) throws IOException {
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                Path out = PathGuard.resolve(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
    }

    /**
     * Runs a process, failing with its stderr on a non-zero exit.
     */
    private void run(List<String> command, Path workingDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        // A local or bare clone makes git spawn `git-upload-pack` / `git-receive-pack`
        // as child processes. On some Windows installs those helpers are not on PATH
        // (git finds them via its own exec path, but a JVM-launched child inherits a
        // different PATH), so git reports "git-upload-pack: command not found". Point
        // GIT_EXEC_PATH at git's exec directory *and* prepend it to PATH, which is
        // what reliably fixes the lookup on Windows without touching the user's PATH.
        String execPath = gitExecPath();
        if (execPath != null) {
            if (System.getenv("GIT_EXEC_PATH") == null) {
                builder.environment().put("GIT_EXEC_PATH", execPath);
            }
            String path = builder.environment().get("PATH");
            if (path == null) path = builder.environment().get("Path");
            if (path == null || !path.contains(execPath)) {
                String separator = java.io.File.pathSeparator;
                builder.environment().put("PATH", execPath + separator + (path == null ? "" : path));
            }
        }
        Process process = builder.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        try {
            if (!process.waitFor(TIMEOUT.toSeconds() + 30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out running: " + String.join(" ", command));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted running: " + String.join(" ", command), ex);
        }
        if (process.exitValue() != 0) {
            throw new IOException("Command failed (" + process.exitValue() + "): "
                    + String.join(" ", command) + "\n" + output.strip());
        }
    }

    /** Cached result of {@code git --exec-path}. */
    private static volatile String gitExecPath;
    /** Whether {@code git --exec-path} has already been attempted. */
    private static volatile boolean gitExecPathProbed;

    /**
     * Returns git's exec directory, where the {@code git-upload-pack} and
     * {@code git-receive-pack} helpers live, or null when it cannot be determined.
     * @return the exec path, or null.
     */
    private static String gitExecPath() {
        if (!gitExecPathProbed) {
            synchronized (Fetcher.class) {
                if (!gitExecPathProbed) {
                    try {
                        Process process = new ProcessBuilder("git", "--exec-path")
                                .redirectErrorStream(true).start();
                        String output;
                        try (InputStream in = process.getInputStream()) {
                            output = new String(in.readAllBytes()).strip();
                        }
                        if (process.waitFor() == 0 && !output.isBlank()) {
                            gitExecPath = output;
                        }
                    } catch (IOException ex) {
                        // git is absent; leave null.
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    gitExecPathProbed = true;
                }
            }
        }
        return gitExecPath;
    }

    /** Fails clearly when git is not on PATH. */
    private void requireGit() throws IOException {
        if (!hasGit()) {
            throw new IOException(
                    "git is required to fetch a plugin from GitHub or a git URL, but it was not found on PATH. "
                            + "Install git, or use a local directory/file marketplace.");
        }
    }

    /** Returns whether a git executable is available. */
    private boolean hasGit() {
        try {
            Process process = new ProcessBuilder(OS.isWindows() ? "where" : "which", "git")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Backs off between retries: 250ms, 500ms. */
    private void backoff(int attempt) {
        try {
            Thread.sleep(250L * (1L << (attempt - 1)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** A short stable hash for cache directory names. */
    private static String hash(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * Deletes a path and everything under it.
     * @param path the path to delete.
     * @throws IOException if a file cannot be deleted.
     */
    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        List<Path> paths = new java.util.ArrayList<>();
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(paths::add);
        }
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }
}
