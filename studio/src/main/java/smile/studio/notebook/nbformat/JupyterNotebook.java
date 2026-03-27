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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

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
public record JupyterNotebook(
        @JsonProperty("cells") List<Cell> cells,
        @JsonProperty("metadata") Metadata metadata,
        @JsonProperty("nbformat") int nbformat,
        @JsonProperty("nbformat_minor") int nbformatMinor
) {
    /** For JSON serialization and deserialization. */
    private static final ObjectMapper mapper = new ObjectMapper();
    /** The current (latest) major format version. */
    public static final int NBFORMAT = 5;
    /** The current (latest) minor format version. */
    public static final int NBFORMAT_MINOR = 10;

    /**
     * Reads a notebook file.
     * @param path the path to the notebook file.
     * @return the notebook read from the specified path.
     */
    public JupyterNotebook from(Path path) throws IOException {
        return mapper.readValue(path, JupyterNotebook.class);
    }

    /**
     * Writes the notebook to the specified file.
     * @param path the file path to write the notebook to.
     */
    public void write(Path path) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path, this);
    }
}
