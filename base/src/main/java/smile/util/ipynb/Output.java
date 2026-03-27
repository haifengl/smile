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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A single output produced by executing a {@link CodeCell}. The output type
 * is determined by the {@code output_type} field and can be one of:
 * <ul>
 *   <li>{@code "stream"} — stdout/stderr text ({@link StreamOutput})</li>
 *   <li>{@code "display_data"} — rich display data ({@link DisplayDataOutput})</li>
 *   <li>{@code "execute_result"} — the result of evaluating an expression ({@link ExecuteResultOutput})</li>
 *   <li>{@code "error"} — an exception raised during execution ({@link ErrorOutput})</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "output_type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamOutput.class, name = "stream"),
        @JsonSubTypes.Type(value = DisplayDataOutput.class, name = "display_data"),
        @JsonSubTypes.Type(value = ExecuteResultOutput.class, name = "execute_result"),
        @JsonSubTypes.Type(value = ErrorOutput.class, name = "error")
})
public sealed interface Output permits StreamOutput, DisplayDataOutput, ExecuteResultOutput, ErrorOutput {

    /**
     * Returns the output type identifier.
     * @return one of {@code "stream"}, {@code "display_data"},
     *         {@code "execute_result"}, or {@code "error"}.
     */
    @JsonProperty("output_type")
    String outputType();
}

