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
package smile.onnx;

/**
 * Graph optimization level for an ONNX Runtime session, corresponding to
 * {@code GraphOptimizationLevel} in the ONNX Runtime C API.
 *
 * <p>Higher optimization levels may increase session initialization time
 * but typically reduce inference latency.
 *
 * @author Haifeng Li
 */
public enum GraphOptimizationLevel {
    /** Disable all optimizations. */
    DISABLE_ALL(0),
    /** Enable basic optimizations (redundant node eliminations). */
    ENABLE_BASIC(1),
    /** Enable extended optimizations (complex node fusions). */
    ENABLE_EXTENDED(2),
    /** Enable layout optimizations. */
    ENABLE_LAYOUT(3),
    /** Enable all available optimizations. */
    ENABLE_ALL(99);

    /** The ORT integer code. */
    private final int value;

    /**
     * Constructor.
     * @param value the ORT integer code.
     */
    GraphOptimizationLevel(int value) {
        this.value = value;
    }

    /**
     * Returns the ORT integer code.
     * @return the ORT integer code.
     */
    public int value() {
        return value;
    }
}

