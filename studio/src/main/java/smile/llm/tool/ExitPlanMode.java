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

import java.nio.file.Files;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import smile.llm.Conversation;

@JsonClassDescription("""
Use this tool when you are in plan mode and have finished writing your plan to the plan file and are ready for user approval.

## How This Tool Works
- You should have already written your plan to the plan file specified in the plan mode system message
- This tool does NOT take the plan content as a parameter - it will read the plan from the file you wrote
- This tool simply signals that you're done planning and ready for the user to review and approve
- The user will see the contents of your plan file when they review it

## When to Use This Tool
IMPORTANT: Only use this tool when the task requires planning the implementation steps of a task that requires writing code. For research tasks where you're gathering information, searching files, reading files or in general trying to understand the codebase - do NOT use this tool.

## Before Using This Tool
Ensure your plan is complete and unambiguous:
- If you have unresolved questions about requirements or approach, use AskUserQuestion first (in earlier phases)
- Once your plan is finalized, use THIS tool to request approval

**Important:** Do NOT use AskUserQuestion to ask "Is this plan okay?" or "Should I proceed?" - that's exactly what THIS tool does. ExitPlanMode inherently requests user approval of your plan.

## Examples

1. Initial task: "Search for and understand the implementation of vim mode in the codebase" - Do not use the exit plan mode tool because you are not planning the implementation steps of a task.
2. Initial task: "Help me implement yank mode for vim" - Use the exit plan mode tool after you have finished planning the implementation steps of the task.
3. Initial task: "Add a new feature to handle user authentication" - If unsure about auth method (OAuth, JWT, etc.), use AskUserQuestion first, then use exit plan mode tool after clarifying the approach.
""")
public class ExitPlanMode implements Tool {
    @Override
    public String run(Conversation conversation, ToolCallListener listener) {
        if (!conversation.planMode()) {
            return "Invalid tool call of ExitPlanMode as agent is not in plan mode.";
        }

        try {
            var path = conversation.planFile();
            if (path.isPresent()) {
                if (!Files.exists(path.get())) {
                    return "Error exiting plan mode: Plan file " + path.get() + " doesn't exist.";
                }
                String plan = Files.readString(path.get());
                String question = "Your plan is ready for review. Please review the plan below and let me know if you approve it or if you have any feedback or questions about it."
                        + "\n\n" + plan;
                List<String> choices = List.of("Approve", "Request changes");
                Question dialog = new Question(question, choices, false);
                listener.onQuestion(dialog);
                String result = dialog.ask().get();
                if (choices.getFirst().equals(result)) {
                    conversation.exitPlanMode(plan);
                    return "User has approved your plan. You can now proceed to implement the plan.";
                } else {
                    return "User request changes: " + result + ". Please continue refining the plan.";
                }
            } else {
                return "Error exiting plan mode: Plan file path is not available.";
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
