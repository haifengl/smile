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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests git {@code sha} pinning against a local repository, so the test needs no
 * network. A pinned commit must be checked out exactly, independent of any branch
 * that has since moved (the two-commit scenario: commit A, then commit B; pin A).
 *
 * <p>A local clone makes git spawn the {@code git-upload-pack} helper as a child
 * process. Some JVM environments (notably a launcher whose child PATH does not
 * include git's exec directory) cannot resolve that helper, so these tests are
 * skipped when a probe clone fails. Real installs clone over HTTPS, where the
 * helper runs on the server and this does not arise.
 *
 * @author Haifeng Li
 */
public class FetcherGitTest {

    @Test
    public void testPinnedShaChecksOutExactCommit(@TempDir Path root) throws IOException {
        Assumptions.assumeTrue(gitAvailable(), "git is not on PATH");
        Assumptions.assumeTrue(localCloneWorks(root), "local git transport is unavailable in this JVM");

        // Given: a repo with two commits that change the same file.
        Path repo = root.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        git(repo, "config", "user.email", "t@example.com");
        git(repo, "config", "user.name", "T");
        git(repo, "checkout", "-q", "-b", "main");
        Files.writeString(repo.resolve("VERSION"), "first\n");
        git(repo, "add", "VERSION");
        git(repo, "commit", "-q", "-m", "first");
        String firstSha = git(repo, "rev-parse", "HEAD").strip();

        Files.writeString(repo.resolve("VERSION"), "second\n");
        git(repo, "add", "VERSION");
        git(repo, "commit", "-q", "-m", "second");

        // When: fetch the plugin pinned to the first commit, while the branch is at
        // the second.
        Path cache = root.resolve("cache");
        Fetcher fetcher = new Fetcher();
        Path checkout = fetcher.pluginRoot(
                new PluginSource.Url(repo.toUri().toString(), "main", firstSha),
                root, cache);

        // Then: the pinned commit's content is present, not the branch head's.
        assertEquals("first\n", Files.readString(checkout.resolve("VERSION")).replace("\r\n", "\n"));
    }

    @Test
    public void testRefWithoutShaFollowsBranch(@TempDir Path root) throws IOException {
        Assumptions.assumeTrue(gitAvailable(), "git is not on PATH");
        Assumptions.assumeTrue(localCloneWorks(root), "local git transport is unavailable in this JVM");

        // Given
        Path repo = root.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        git(repo, "config", "user.email", "t@example.com");
        git(repo, "config", "user.name", "T");
        git(repo, "checkout", "-q", "-b", "main");
        Files.writeString(repo.resolve("VERSION"), "head\n");
        git(repo, "add", "VERSION");
        git(repo, "commit", "-q", "-m", "only");

        // When
        Path checkout = new Fetcher().pluginRoot(
                new PluginSource.Url(repo.toUri().toString(), "main", null), root, root.resolve("cache"));

        // Then
        assertEquals("head\n", Files.readString(checkout.resolve("VERSION")).replace("\r\n", "\n"));
    }

    /** Runs a git command in a directory and returns stdout. */
    private static String git(Path dir, String... args) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("git " + String.join(" ", args) + " failed: " + output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted running git", ex);
        }
        return output;
    }

    /** Returns whether git is available. */
    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
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

    /**
     * Probes whether a local clone works in this JVM, by cloning a throwaway repo.
     * Guards against the {@code git-upload-pack} child-process lookup that fails in
     * some sandboxes.
     *
     * @param root a scratch directory.
     * @return true when a local clone succeeds.
     */
    private static boolean localCloneWorks(Path root) {
        try {
            Path source = root.resolve("probe-src");
            Files.createDirectories(source);
            git(source, "init", "-q");
            git(source, "config", "user.email", "t@example.com");
            git(source, "config", "user.name", "T");
            Files.writeString(source.resolve("f.txt"), "x\n");
            git(source, "add", "f.txt");
            git(source, "commit", "-q", "-m", "probe");
            git(root, "clone", "-q", source.toUri().toString(), root.resolve("probe-clone").toString());
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
