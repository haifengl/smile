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
 * Logging severity level for the ONNX Runtime environment, corresponding to
 * {@code OrtLoggingLevel} in the ONNX Runtime C API.
 *
 * @author Haifeng Li
 */
public enum LoggingLevel {
    /** Verbose output. */
    VERBOSE(0),
    /** Informational messages. */
    INFO(1),
    /** Warning messages. */
    WARNING(2),
    /** Error messages. */
    ERROR(3),
    /** Fatal error messages only. */
    FATAL(4);

    /** The ORT integer code. */
    private final int value;

    /**
     * Constructor.
     * @param value the ORT integer code.
     */
    LoggingLevel(int value) {
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

