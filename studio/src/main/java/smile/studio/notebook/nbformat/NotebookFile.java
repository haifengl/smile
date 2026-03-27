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
import java.util.List;

/**
 * The top-level Jupyter notebook document (nbformat 5).
 * <p>
 * A notebook document consists of an ordered list of cells, metadata about
 * the notebook, and version information. The current major version is
 * {@code 5} with the minor version indicating incremental additions (e.g.
 * {@code 5.4} added the cell {@code id} field as a required property).
 *
 * @param cells         the list of cells in document order.
 * @param metadata      the notebook-level metadata.
 * @param nbformat      the major version of the notebook format ({@code 5}).
 * @param nbformatMinor the minor version of the notebook format.
 *
 * @see <a href="https://nbformat.readthedocs.io/en/latest/format_description.html">nbformat 5 spec</a>
 * @author Haifeng Li
 */
public record NotebookFile(
        @JsonProperty("cells") List<Cell> cells,
        @JsonProperty("metadata") NotebookMetadata metadata,
        @JsonProperty("nbformat") int nbformat,
        @JsonProperty("nbformat_minor") int nbformatMinor
) {
    /** The current (latest) major format version. */
    public static final int NBFORMAT = 5;
    /** The current (latest) minor format version. */
    public static final int NBFORMAT_MINOR = 5;
}

