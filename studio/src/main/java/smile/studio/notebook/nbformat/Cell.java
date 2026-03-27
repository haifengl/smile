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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A single cell in a Jupyter notebook. The cell type is determined by the
 * {@code cell_type} field and can be one of {@code "code"}, {@code "markdown"},
 * or {@code "raw"}.
 *
 * @author Haifeng Li
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "cell_type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CodeCell.class, name = "code"),
        @JsonSubTypes.Type(value = MarkdownCell.class, name = "markdown"),
        @JsonSubTypes.Type(value = RawCell.class, name = "raw")
})
public sealed interface Cell permits CodeCell, MarkdownCell, RawCell {

    /**
     * Returns the cell type identifier.
     * @return {@code "code"}, {@code "markdown"}, or {@code "raw"}.
     */
    @JsonProperty("cell_type")
    String cellType();

    /**
     * Returns the cell-level metadata.
     * @return the cell metadata.
     */
    @JsonProperty("metadata")
    CellMetadata metadata();

    /**
     * Returns the source content of the cell as a multiline string.
     * In the notebook JSON this is stored as a list of strings or a
     * single string; Jackson handles both via {@link MultilineString}.
     *
     * @return the cell source lines.
     */
    @JsonProperty("source")
    MultilineString source();

    /**
     * Returns the unique cell identifier (nbformat &ge; 4.5).
     * @return the cell id, or {@code null} for older notebooks.
     */
    @JsonProperty("id")
    String id();
}

