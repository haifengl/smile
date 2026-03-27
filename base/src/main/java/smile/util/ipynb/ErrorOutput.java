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
 * An error output produced when a cell raises an exception during execution
 * (output type {@code "error"}).
 *
 * @param ename     the exception class name (e.g. {@code "NameError"}).
 * @param evalue    the exception message / value.
 * @param traceback the list of traceback frame strings, each possibly containing
 *                  ANSI escape sequences for color.
 *
 * @author Haifeng Li
 */
public record ErrorOutput(
        @JsonProperty("ename") String ename,
        @JsonProperty("evalue") String evalue,
        @JsonProperty("traceback") List<String> traceback
) implements Output {

    @Override
    public String outputType() {
        return "error";
    }
}

