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
package smile.onnx.genai;

/**
 * A GenAI execution-provider candidate for {@link Model#open} cascades.
 *
 * <p>Implementations must gate on shared-library <em>presence</em> only — never
 * eagerly {@link System#load} EP DLLs (that can hard-crash the JVM).
 *
 * @author Haifeng Li
 */
interface GenAIProviderCandidate {
    /**
     * Stable id recorded on {@link Model#provider()} (e.g. {@code cuda},
     * {@code ryzenai}).
     *
     * @return provider id.
     */
    String id();

    /**
     * Returns whether matching EP natives appear to be on the library path.
     *
     * @return {@code true} when an open attempt is worth trying.
     */
    boolean nativesPresent();

    /**
     * Configures {@code config} for this provider (typically
     * {@link Config#clearProviders()} + {@link Config#appendProvider(String)}).
     *
     * @param config   mutable config owned by the caller.
     * @param modelDir GenAI model directory (for config-driven paths such as RyzenAI).
     */
    void configure(Config config, String modelDir);

    /**
     * Returns whether this candidate should be attempted: natives present and
     * not previously marked unusable.
     *
     * @return {@code true} to try loading with this provider.
     */
    default boolean shouldAttempt() {
        Boolean usable = GenAIProviders.usable(id());
        if (Boolean.FALSE.equals(usable)) {
            return false;
        }
        if (Boolean.TRUE.equals(usable)) {
            return true;
        }
        return nativesPresent();
    }
}
