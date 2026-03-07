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

import java.util.List;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;

@JsonClassDescription("""
Use this tool when you are in plan mode and have finished presenting your plan and are ready to code. This will prompt the user to exit plan mode.
IMPORTANT: Only use this tool when the task requires planning the implementation steps of a task that requires writing code. For research tasks where you're gathering information, searching files, reading files or in general trying to understand the codebase - do NOT use this tool.

Eg.
1. Initial task: "Search for and understand the implementation of vim mode in the codebase" - Do not use the exit plan mode tool because you are not planning the implementation steps of a task.
2. Initial task: "Help me implement yank mode for vim" - Use the exit plan mode tool after you have finished planning the implementation steps of the task.
""")
public class ExitPlanMode implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The plan you came up with, that you want to run by the user for approval. Supports markdown. The plan should be pretty concise.")
    public String plan;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        try {
            var path = conversation.exitPlanMode(plan);
            if (path != null) {
                return "User has approved your plan. You can now proceed to implement the plan. I've saved your plan to " + path.toAbsolutePath() + " for your reference.";
            } else {
                return "Invalid tool call of ExitPlanMode as agent is not in plan mode.";
            }
        } catch (Exception ex) {
            return "Error to exit the plan mode: " + ex.getMessage();
        }
    }

    /**
     * The specification for ExitPlanMode tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        return new Tool.Spec(ExitPlanMode.class, List.of());
    }
}
