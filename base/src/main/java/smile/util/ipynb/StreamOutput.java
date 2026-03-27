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

/**
 * A stream output produced by {@code stdout} or {@code stderr} during cell
 * execution (output type {@code "stream"}).
 *
 * @param name the stream name: {@code "stdout"} or {@code "stderr"}.
 * @param text the text content written to the stream.
 *
 * @author Haifeng Li
 */
public record StreamOutput(
        @JsonProperty("name") String name,
        @JsonProperty("text") MultilineString text
) implements Output {

    @Override
    public String outputType() {
        return "stream";
    }
}

