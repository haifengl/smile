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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Source file and optional explicit identity overrides for a registered model version.
 *
 * <p>Retaining {@code slugOverride} and {@code versionOverride} across unload/reload
 * ensures that models registered via {@code models.json}, {@code served-model-id},
 * versioned directory layout, or {@code POST /v1/models/load} keep their assigned
 * identity when reloaded from disk.
 *
 * @param path            normalized absolute path to the backing model artifact.
 * @param slugOverride    explicitly assigned slug, or {@code null} to derive from artifact metadata/filename.
 * @param versionOverride explicitly assigned version, or {@code null} to derive from artifact metadata/default.
 * @author Haifeng Li
 */
public record ModelSource(Path path, String slugOverride, String versionOverride) {

    /** Compact constructor normalizing the path and blank overrides. */
    public ModelSource {
        Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
        slugOverride = (slugOverride != null && !slugOverride.isBlank()) ? ModelRef.validateSlug(slugOverride) : null;
        versionOverride = (versionOverride != null && !versionOverride.isBlank()) ? ModelRef.validateVersion(versionOverride) : null;
    }

    /**
     * Creates a source with no explicit overrides (identity derived from artifact metadata/filename).
     *
     * @param path model file path.
     * @return model source.
     */
    public static ModelSource of(Path path) {
        return new ModelSource(path, null, null);
    }

    /**
     * Returns {@code true} if either slug or version was explicitly overridden.
     *
     * @return {@code true} when explicit identity overrides are present.
     */
    public boolean hasExplicitOverride() {
        return slugOverride != null || versionOverride != null;
    }
}
