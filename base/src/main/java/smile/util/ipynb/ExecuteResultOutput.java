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
 * An execute-result output (output type {@code "execute_result"}).
 * <p>
 * This is the result of evaluating an expression and is similar to
 * {@link DisplayDataOutput} but also carries the {@code execution_count}
 * (prompt number) that links the output back to its code cell.
 *
 * @param executionCount the cell execution counter at the time the output was produced.
 * @param data           the MIME-bundle mapping MIME types to their content.
 * @param metadata       optional MIME-type-specific metadata.
 * @param transientData  transient data not saved with the notebook (e.g. display ids).
 *
 * @author Haifeng Li
 */
public record ExecuteResultOutput(
        @JsonProperty("execution_count") Integer executionCount,
        @JsonProperty("data") Map<String, JsonNode> data,
        @JsonProperty("metadata") Map<String, JsonNode> metadata,
        @JsonProperty("transient") Map<String, JsonNode> transientData
) implements Output {

    @Override
    public String outputType() {
        return "execute_result";
    }
}

