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

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A code cell. Code cells contain executable source code in the kernel language
 * and may carry a list of outputs generated during execution.
 * <p>
 * The {@code id} field is required in nbformat 5 (nbformat_minor &ge; 4).
 *
 * @param id             the unique cell identifier (required in nbformat 5).
 * @param metadata       the cell-level metadata.
 * @param source         the source code of the cell.
 * @param outputs        the list of outputs produced by executing the cell.
 * @param executionCount the prompt number; {@code null} if the cell has never been executed.
 *
 * @author Haifeng Li
 */
public record CodeCell(
        @JsonProperty("id") String id,
        @JsonProperty("metadata") CellMetadata metadata,
        @JsonProperty("source") MultilineString source,
        @JsonProperty("outputs") List<Output> outputs,
        @JsonProperty("execution_count") Integer executionCount
) implements Cell {

    @Override
    public String cellType() {
        return "code";
    }
}

