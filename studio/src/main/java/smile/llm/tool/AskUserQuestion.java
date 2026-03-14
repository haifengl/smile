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
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JOptionPane;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;
import smile.util.OS;

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
    @JsonPropertyDescription("Clear, concise description of what this command does in 5-10 words, in active voice. Examples:\\nInput: ls\\nOutput: List files in current directory\\n\\nInput: git status\\nOutput: Show working tree status\\n\\nInput: npm install\\nOutput: Install package dependencies\\n\\nInput: mkdir foo\\nOutput: Create directory 'foo")
    public String question;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The choices to execute")
    public List<String> choices;

    @JsonPropertyDescription("Set to true to allow multiple answers to be selected for a question.")
    public boolean multiSelect = false;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        return askUserQuestion(question, choices, multiSelect);
    }

    /** Static helper method to ask user a question. */
    public static String askUserQuestion(String question, List<String> choices, boolean multiSelect) {
        QuestionDialog dialog = new QuestionDialog(question, choices, multiSelect);
        dialog.setVisible(true); // Blocks until dialog is closed
        String result = dialog.getSelectedValue();
        if (result != null) {
            return "User has answered your question: " + result + ". You can now continue with the user's answers in mind.";
        } else {
            return "Usr didn't answer your question.";
        }
    }

    /**
     * The specification for AskUserQuestion tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(AskUserQuestion.class,
                    List.of(AskUserQuestion.class.getMethod("askUserQuestion", String.class, List.class, boolean.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(AskUserQuestion.class, List.of());
    }
}
