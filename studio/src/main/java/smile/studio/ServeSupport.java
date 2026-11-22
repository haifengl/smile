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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Pure helper functions for the inference service lifecycle: settings
 * normalization, the attached-engine banner, and the configured-model drift check.
 *
 * <p>Kept out of {@link ServeManager} so the state machine there stays readable
 * and these rules can be tested without a running engine.
 *
 * @author Haifeng Li
 */
final class ServeSupport {

    private ServeSupport() {
    }

    /**
     * Applies the default host when none is configured.
     *
     * @param host the configured host, possibly null or blank.
     * @return the effective host.
     */
    static String normalizeHost(String host) {
        return (host == null || host.isBlank()) ? StudioConfig.DEFAULT_HOST : host.trim();
    }

    /**
     * Applies the default port when none is configured.
     *
     * @param port the configured port; non-positive means unset.
     * @return the effective port.
     */
    static int normalizePort(int port) {
        return port > 0 ? port : StudioConfig.DEFAULT_PORT;
    }

    /**
     * Normalizes the optional LLM setting.
     *
     * @param llm the configured model id, possibly null.
     * @return the trimmed model id, or an empty string.
     */
    static String normalizeLlm(String llm) {
        return llm == null ? "" : llm.trim();
    }

    /**
     * Compares the configured LLM with a known model catalog.
     *
     * @param loaded        the model ids reported by the engine; must not be null.
     * @param configuredLlm the configured LLM id, possibly blank.
     * @return the configured model id when it is not loaded, otherwise empty.
     */
    static Optional<String> driftWarning(List<String> loaded, String configuredLlm) {
        if (configuredLlm == null || configuredLlm.isBlank()) {
            return Optional.empty();
        }
        String model = configuredLlm.trim();
        if (loaded.stream().anyMatch(id -> id.equals(model))) {
            return Optional.empty();
        }
        return Optional.of(model);
    }

    /**
     * Compares the configured LLM with a catalog that may be unreadable.
     *
     * <p>An unreadable catalog reports no drift, so a transient network failure is
     * never shown to the user as a model mismatch. Named distinctly from the
     * {@link List} overload so a {@code null} argument is never ambiguous.
     *
     * @param catalog       the catalog, or empty when the engine did not answer.
     * @param configuredLlm the configured LLM id, possibly blank.
     * @return the configured model id when it is not loaded, otherwise empty.
     */
    static Optional<String> driftWarningForCatalog(Optional<List<String>> catalog, String configuredLlm) {
        return catalog.flatMap(models -> driftWarning(models, configuredLlm));
    }

    /**
     * Polls a readiness probe until it succeeds, the child exits, or time runs out.
     *
     * <p>Extracted from the manager so the timeout and early-exit behaviour can be
     * tested without spawning a real engine.
     *
     * @param child      the spawned child process.
     * @param ready      the readiness probe.
     * @param timeout    how long to keep polling.
     * @param pollMillis the interval between attempts.
     * @return {@code true} when the child reported ready before the timeout.
     */
    static boolean awaitReady(Process child, BooleanSupplier ready, Duration timeout, long pollMillis) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (!child.isAlive()) {
                return false;
            }
            if (ready.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Renders the banner shown in the console when attached to a foreign engine.
     *
     * @param baseUrl the engine base URL.
     * @param models  the models the engine reports.
     * @param llm     the configured LLM, possibly empty.
     * @param host    the configured host.
     * @return the text to display.
     */
    static String attachedPanelText(String baseUrl, List<String> models, String llm, String host) {
        StringBuilder sb = new StringBuilder("[Inference Service] Attached to a running service at ")
                .append(baseUrl).append('\n');
        if (models == null || models.isEmpty()) {
            sb.append("[Inference Service] No models are currently loaded.\n");
        } else {
            sb.append("[Inference Service] Loaded models: ").append(String.join(", ", models)).append('\n');
        }
        if (driftWarning(models, llm).isPresent()) {
            sb.append("[Inference Service] The configured model '").append(llm)
                    .append("' is not loaded in this engine.\n");
        }
        if (!LocalHosts.isLocalHost(host)) {
            sb.append("[Inference Service] Remote engine: model load, reload, and unload are restricted to localhost.\n");
        }
        return sb.toString();
    }
}
