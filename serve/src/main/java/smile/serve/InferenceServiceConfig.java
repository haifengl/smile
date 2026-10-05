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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The inference service configuration.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.serve")
public interface InferenceServiceConfig {
    /** The location of pre-trained model(s) for inference. */
    String model();

    /**
     * Whether the service may start with no model loaded.
     *
     * <p>Defaults to {@code false}: a standalone {@code smile serve} that finds
     * no model exits at startup rather than idling with nothing to serve. Set
     * {@code smile.serve.allow-empty=true} for dynamic-load mode, where models
     * arrive later via {@code POST /api/v1/models/load} — Studio starts the
     * service this way so one process can serve many models.
     *
     * @return {@code true} when an empty catalog is permitted at startup.
     */
    @WithDefault("false")
    boolean allowEmpty();
}
