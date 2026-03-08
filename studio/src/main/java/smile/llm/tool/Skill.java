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
Load a specialized skill that provides domain-specific instructions and workflows.

When you recognize that a task matches one of the available skills listed below, use this tool to load the full skill instructions.

The skill will inject detailed instructions, workflows, and access to bundled resources (scripts, references, templates) into the conversation context.

Tool output includes a `<skill_content name="...">` block with the loaded content.

The following skills provide specialized sets of instructions for particular tasks
Invoke this tool to load a skill when a task matches one of the available skills.
""")
public class Skill implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the file to read")
    public String skill;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        statusUpdate.accept("Loading Skill " + skill);
        return loadSkill(skill);
    }

    /** Static helper method to load a skill. */
    public static String loadSkill(String name) {
        return "Error: Skill '" + name + "' not found. Please check the skill name and try again.";
    }

    /**
     * The specification for Skill tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(Skill.class,
                    List.of(Skill.class.getMethod("loadSkill", String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(Skill.class, List.of());
    }
}
