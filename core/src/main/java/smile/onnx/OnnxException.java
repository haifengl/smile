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
 * Exception thrown when an ONNX Runtime operation fails.
 *
 * @author Haifeng Li
 */
public class OnnxException extends RuntimeException {
    /** The ORT error code. */
    private final int errorCode;

    /**
     * Constructor.
     * @param errorCode the ORT error code.
     * @param message the error message.
     */
    public OnnxException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructor.
     * @param message the error message.
     * @param cause the cause.
     */
    public OnnxException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    /**
     * Returns the ORT error code.
     * @return the ORT error code.
     */
    public int errorCode() {
        return errorCode;
    }
}

