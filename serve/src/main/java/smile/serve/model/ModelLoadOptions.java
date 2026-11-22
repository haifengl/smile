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

import java.util.List;

/**
 * Optional identity and routing parameters for dynamic model loading via
 * {@code POST /v1/models/load}.
 *
 * @param slug       optional slug override (single-artifact loads only).
 * @param version    optional version override (single-artifact loads only).
 * @param aliases    optional list of alias names to assign to the newly loaded version.
 * @param setDefault optional default-routing control: {@code Boolean.TRUE} pins {@code default}
 *                   to the loaded version; {@code Boolean.FALSE} pins the existing default
 *                   so the new version does not hijack default traffic; {@code null} leaves
 *                   default policy unchanged.
 * @author Haifeng Li
 */
public record ModelLoadOptions(
        String slug,
        String version,
        List<String> aliases,
        Boolean setDefault) {

    /** Empty options (derive slug/version from layout/metadata/filename, keep default policy). */
    public static final ModelLoadOptions EMPTY = new ModelLoadOptions(null, null, List.of(), null);

    public ModelLoadOptions {
        slug = (slug != null && !slug.isBlank()) ? ModelRef.validateSlug(slug) : null;
        version = (version != null && !version.isBlank()) ? ModelRef.validateVersion(version) : null;
        aliases = aliases == null
                ? List.of()
                : aliases.stream()
                         .filter(a -> a != null && !a.isBlank())
                         .map(ModelRef::validateAlias)
                         .distinct()
                         .toList();
    }

    /** Returns {@code true} if any single-artifact override or alias was specified. */
    public boolean hasSingleArtifactOverrides() {
        return slug != null || version != null || !aliases.isEmpty() || setDefault != null;
    }
}
