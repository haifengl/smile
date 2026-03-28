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
package smile.util.ipynb;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * A raw cell. Raw cells contain content that should be passed through
 * unmodified by nbconvert. The target conversion format is indicated by
 * {@link CellMetadata#rawMimetype()} (nbformat 5) or the legacy
 * {@link CellMetadata#format()} field.
 * <p>
 * Since nbformat 4.5 / nbformat 5, raw cells may also carry
 * {@code attachments}: inline files stored as a map of filename → MIME bundle.
 *
 * @param id          the unique cell identifier (nbformat &ge; 4.5, required in nbformat 5).
 * @param metadata    the cell-level metadata. Use {@link CellMetadata#rawMimetype()} to
 *                    specify the target MIME type (e.g. {@code "text/restructuredtext"}).
 * @param source      the raw source content of the cell.
 * @param attachments optional map of filename to MIME bundle for inline attachments.
 *
 * @author Haifeng Li
 */
public record RawCell(
        @JsonProperty("id") String id,
        @JsonProperty("metadata") CellMetadata metadata,
        @JsonProperty("source") MultilineString source,
        @JsonProperty("attachments") Map<String, Map<String, JsonNode>> attachments
) implements Cell {

    @Override
    public String cellType() {
        return "raw";
    }
}

