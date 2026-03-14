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

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;

@JsonClassDescription("""
Use this tool when you need to ask the user questions during execution. This allows you to:
1. Gather user preferences or requirements
2. Clarify ambiguous instructions
3. Get decisions on implementation choices as you work
4. Offer choices to the user about what direction to take.

Usage notes:
- Users will always be able to select "Other" to provide custom text input
- Use multiSelect: true to allow multiple answers to be selected for a question
- If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label

Plan mode note: In plan mode, use this tool to clarify requirements or choose between approaches BEFORE finalizing your plan. Do NOT use this tool to ask "Is my plan ready?" or "Should I proceed?" If you need plan approval, use ExitPlanMode instead. IMPORTANT: Do not reference "the plan" in your questions (e.g., "Do you have feedback about the plan?", "Does the plan look good?") because the user cannot see the plan in the UI until you call ExitPlanMode.
""")
public class AskUserQuestion implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Clear, concise description of the question you want to ask the user.")
    public String question;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The available choices for the user to select. Users will also have the option to select 'Other' to provide custom input.")
    public List<String> choices;

    @JsonPropertyDescription("Set to true to allow multiple answers to be selected for a question.")
    public boolean multiSelect = false;

    @Override
    public String run(Conversation conversation, ToolCallListener listener) {
        // Ensure "Other" is always an option for users to provide custom input
        if (!choices.contains(Question.OTHER)) {
            // In case the original list is immutable, create a new mutable list.
            choices = new ArrayList<>(choices);
            choices.add(Question.OTHER);
        }

        Question dialog = new Question(question, choices, multiSelect);
        listener.onQuestion(dialog);
        try {
            String result = dialog.ask().get();
            if (result != null) {
                return "User has answered your question: " + result + ". You can now continue with the user's answers in mind.";
            } else {
                return "Usr didn't answer your question.";
            }
        } catch (Exception e) {
            return "Error while asking user question: " + e.getMessage();
        }
    }

    /**
     * The specification for AskUserQuestion tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        return new Tool.Spec(AskUserQuestion.class, List.of());
    }
}
