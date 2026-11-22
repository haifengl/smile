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
package smile.serve;

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the ONNX inference service.
 * All properties are prefixed with {@code smile.onnx}.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.onnx")
public interface OnnxServiceConfig {
    /**
     * Path to a single {@code .onnx} file or a directory containing
     * {@code .onnx} files to load at startup.
     * Defaults to {@code "../model"} (same as the SMILE model directory).
     */
    @WithDefault("../model")
    String model();

    /**
     * Optional public model ID override when serving a single {@code .onnx} model.
     * When set and exactly one {@code .onnx} model is loaded, it is registered
     * under this ID instead of the file stem.
     */
    @WithName("served-model-id")
    default Optional<String> servedModelId() {
        return Optional.empty();
    }
}

