/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.notebook.nbformat;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cell-level metadata. All fields are optional; unknown metadata keys are
 * preserved in the {@code collapsed}, {@code scrolled}, and {@code tags}
 * fields where applicable, and any additional properties should be round-tripped
 * transparently when using {@link com.fasterxml.jackson.annotation.JsonAnySetter} /
 * {@link com.fasterxml.jackson.annotation.JsonAnyGetter} on richer implementations.
 *
 * @param collapsed  whether the cell output is collapsed ({@code null} if unset).
 * @param scrolled   whether the cell output is scrolled — can be {@code true},
 *                   {@code false}, or the string {@code "auto"}.
 * @param deletable  whether the cell is deletable from the UI.
 * @param editable   whether the cell is editable.
 * @param format     the MIME type for raw cells (e.g. {@code "text/restructuredtext"}).
 * @param name       an optional name for the cell used by tools.
 * @param tags       a list of string tags for the cell.
 *
 * @author Haifeng Li
 */
public record CellMetadata(
        @JsonProperty("collapsed") Boolean collapsed,
        @JsonProperty("scrolled") Object scrolled,
        @JsonProperty("deletable") Boolean deletable,
        @JsonProperty("editable") Boolean editable,
        @JsonProperty("format") String format,
        @JsonProperty("name") String name,
        @JsonProperty("tags") java.util.List<String> tags,
        @JsonProperty("jupyter") Map<String, Object> jupyter,
        @JsonProperty("execution") Map<String, Object> execution
) {
}

