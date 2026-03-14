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
package smile.llm.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The task definition.
 *
 * @author Haifeng Li
 */
public class Todo {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The description of the task in imperative form")
    public String content;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The status of the task: pending, in_progress, or completed")
    public String status;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The description of the task in active form, shown during execution (e.g., 'Running tests', 'Building the project')")
    public String activeForm;
}
