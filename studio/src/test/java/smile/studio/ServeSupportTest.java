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
package smile.studio;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ServeSupport} and {@link ServeCommand} — the pure parts of
 * the inference service lifecycle, tested without a running engine.
 *
 * @author Haifeng Li
 */
public class ServeSupportTest {

    @Test
    public void testNormalizeHost() {
        // Given / When / Then
        assertEquals(StudioConfig.DEFAULT_HOST, ServeSupport.normalizeHost(null));
        assertEquals(StudioConfig.DEFAULT_HOST, ServeSupport.normalizeHost("  "));
        assertEquals("serve.internal", ServeSupport.normalizeHost("  serve.internal  "));
    }

    @Test
    public void testNormalizePort() {
        // Given / When / Then
        assertEquals(StudioConfig.DEFAULT_PORT, ServeSupport.normalizePort(0));
        assertEquals(StudioConfig.DEFAULT_PORT, ServeSupport.normalizePort(-1));
        assertEquals(9000, ServeSupport.normalizePort(9000));
    }

    @Test
    public void testNormalizeLlm() {
        // Given / When / Then
        assertEquals("", ServeSupport.normalizeLlm(null));
        assertEquals("", ServeSupport.normalizeLlm("   "));
        assertEquals("phi-4-mini", ServeSupport.normalizeLlm("  phi-4-mini "));
    }

    @Test
    public void testDriftWarningIsSilentOnBlankConfig() {
        // Given / When / Then
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), "").isEmpty());
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), null).isEmpty());
    }

    @Test
    public void testDriftWarningMatchesExactSlug() {
        // Given / When / Then: the engine reports exact slugs without synthetic suffixes.
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), "phi-4-mini").isEmpty());
        assertEquals(Optional.of("phi-4-mini"), ServeSupport.driftWarning(List.of("phi-4-mini-extra"), "phi-4-mini"));
    }

    @Test
    public void testDriftWarningReportsMissingModel() {
        // Given / When
        Optional<String> warning = ServeSupport.driftWarning(List.of("phi-4-mini"), "Qwen3.8-27B");

        // Then
        assertEquals(Optional.of("Qwen3.8-27B"), warning);
    }

    @Test
    public void testDriftWarningSuppressedWhenCatalogUnreadable() {
        // Given: the catalog could not be fetched (transient failure).
        // When / Then: no warning, so a network blip is not shown as a mismatch.
        assertTrue(ServeSupport.driftWarningForCatalog(Optional.<List<String>>empty(), "Qwen3.8-27B").isEmpty());
    }

    @Test
    public void testDriftWarningAppliedWhenCatalogReadable() {
        // Given
        Optional<List<String>> catalog = Optional.of(List.of("phi-4-mini"));

        // When / Then
        assertEquals(Optional.of("Qwen3.8-27B"), ServeSupport.driftWarningForCatalog(catalog, "Qwen3.8-27B"));
        assertTrue(ServeSupport.driftWarningForCatalog(catalog, "phi-4-mini").isEmpty());
    }

    @Test
    public void testAttachedPanelTextReportsEmptyCatalog() {
        // Given / When
        String text = ServeSupport.attachedPanelText("http://127.0.0.1:8888", List.of(), "", "127.0.0.1");

        // Then
        assertTrue(text.contains("http://127.0.0.1:8888"));
        assertTrue(text.contains("No models are currently loaded"));
        assertFalse(text.contains("Remote engine"));
    }

    @Test
    public void testAttachedPanelTextListsModels() {
        // Given / When
        String text = ServeSupport.attachedPanelText(
                "http://127.0.0.1:8888", List.of("iris-1", "phi-4-mini"), "", "127.0.0.1");

        // Then
        assertTrue(text.contains("Loaded models: iris-1, phi-4-mini"));
    }

    @Test
    public void testAttachedPanelTextFlagsDrift() {
        // Given / When
        String text = ServeSupport.attachedPanelText(
                "http://127.0.0.1:8888", List.of("phi-4-mini"), "Qwen3.8-27B", "127.0.0.1");

        // Then
        assertTrue(text.contains("Qwen3.8-27B"));
        assertTrue(text.contains("not loaded in this engine"));
    }

    @Test
    public void testAttachedPanelTextFlagsRemoteEngine() {
        // Given / When
        String text = ServeSupport.attachedPanelText(
                "http://192.0.2.10:8888", List.of(), "", "192.0.2.10");

        // Then
        assertTrue(text.contains("Remote engine"));
    }

    @Test
    public void testServeCommandCarriesPortAndHost() {
        // Given
        Path jar = Path.of("serve/quarkus-run.jar");

        // When
        var cmd = ServeCommand.build(jar, "localhost", 8888, "");

        // Then
        assertTrue(cmd.contains("-Dquarkus.http.port=8888"));
        assertTrue(cmd.contains("-Dquarkus.http.host=localhost"));
        assertTrue(cmd.contains("-Dsmile.serve.allow-empty=true"));
        assertTrue(cmd.contains("--enable-native-access"));
        assertTrue(cmd.contains(jar.toString()));
    }

    @Test
    public void testServeCommandOmitsChatModelWhenUnset() {
        // Given / When
        var cmd = ServeCommand.build(Path.of("j.jar"), "localhost", 8888, "");

        // Then
        assertFalse(cmd.stream().anyMatch(arg -> arg.startsWith("-Dsmile.chat.model=")));
    }

    @Test
    public void testServeCommandIncludesChatModelWhenConfigured() {
        // Given / When
        var cmd = ServeCommand.build(Path.of("j.jar"), "localhost", 8888, "phi-4-mini");

        // Then
        assertTrue(cmd.contains("-Dsmile.chat.model=phi-4-mini"));
    }

    @Test
    public void testAwaitReadyReturnsTrueWhenProbeSucceeds() {
        // Given: a child that stays alive and becomes ready immediately.
        // When / Then
        assertTrue(ServeSupport.awaitReady(
                new FakeProcess(true), () -> true, java.time.Duration.ofSeconds(1), 10));
    }

    @Test
    public void testAwaitReadyFailsFastWhenChildExits() {
        // Given: a child that has already exited.
        long start = System.nanoTime();

        // When
        boolean ready = ServeSupport.awaitReady(
                new FakeProcess(false), () -> true, java.time.Duration.ofSeconds(30), 10);

        // Then: the poll stops immediately instead of waiting out the timeout.
        assertFalse(ready);
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - start).toMillis() < 5000);
    }

    @Test
    public void testAwaitReadyTimesOutWhenNeverReady() {
        // Given: a live child whose readiness probe never succeeds.
        long start = System.nanoTime();

        // When
        boolean ready = ServeSupport.awaitReady(
                new FakeProcess(true), () -> false, java.time.Duration.ofMillis(100), 10);

        // Then
        assertFalse(ready);
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - start).toMillis() >= 90);
    }

    /** Minimal live/dead process stand-in for the readiness poll. */
    private static final class FakeProcess extends Process {
        private final boolean alive;

        FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
