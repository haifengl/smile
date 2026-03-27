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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A raw cell. Raw cells contain content that should be passed through
 * unmodified by nbconvert. The intended conversion format is indicated in the
 * cell metadata via the {@code format} field of {@link CellMetadata}.
 *
 * @param id       the unique cell identifier (nbformat &ge; 4.5).
 * @param metadata the cell-level metadata. Use {@link CellMetadata#format()} to
 *                 specify the target MIME type (e.g. {@code "text/restructuredtext"}).
 * @param source   the raw source content of the cell.
 *
 * @author Haifeng Li
 */
public record RawCell(
        @JsonProperty("id") String id,
        @JsonProperty("metadata") CellMetadata metadata,
        @JsonProperty("source") MultilineString source
) implements Cell {

    @Override
    public String cellType() {
        return "raw";
    }
}

