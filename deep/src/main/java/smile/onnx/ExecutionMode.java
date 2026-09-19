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
 * Execution mode for an ONNX Runtime session, corresponding to
 * {@code ExecutionMode} in the ONNX Runtime C API.
 *
 * @author Haifeng Li
 */
public enum ExecutionMode {
    /**
     * Sequential execution mode. Operators are executed one at a time.
     * This is the default and is preferred for most latency-sensitive
     * single-request scenarios.
     */
    SEQUENTIAL(0),

    /**
     * Parallel execution mode. Allows parallel execution of operators
     * within a single session run. May improve throughput for models with
     * parallelizable subgraphs.
     */
    PARALLEL(1);

    /** The ORT integer code. */
    private final int value;

    /**
     * Constructor.
     * @param value the ORT integer code.
     */
    ExecutionMode(int value) {
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

