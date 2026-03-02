/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.mcp;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The type of input variable.
 *
 * @author Haifeng Li
 */
public enum InputType {
    /** A plain text prompt. */
    PROMPTSTRING("promptString"),
    /** A pick-list prompt. */
    PICKSTRING("pickString"),
    /** A command to run for the value. */
    COMMAND("command");

    /** The JSON value. */
    private final String value;

    /**
     * Constructor.
     * @param value the JSON value.
     */
    InputType(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
