/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Diagnostic descriptor for a model artifact on disk that failed verification
 * or deserialization and was quarantined instead of loaded into the active
 * catalog.
 *
 * @param path      the file path on disk.
 * @param namespace {@code "smile"} or {@code "onnx"}.
 * @param error     the exception class name.
 * @param reason    human-readable failure message.
 * @param fatal     {@code true} when the failure is a security, format-version,
 *                  or corruption error that aborts startup under {@code fail-fast=true}.
 * @param timestamp epoch seconds when the failure occurred.
 *
 * @author Haifeng Li
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuarantinedModel(
        String path,
        String namespace,
        String error,
        String reason,
        boolean fatal,
        long timestamp) {
}
