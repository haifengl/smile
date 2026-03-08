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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.agent.Skill;
import smile.llm.Conversation;

@JsonClassDescription("""
Load a specialized skill that provides domain-specific instructions and workflows.

When you recognize that a task matches one of the available skills listed below, use this tool to load the full skill instructions.

The skill will inject detailed instructions, workflows, and access to bundled resources (scripts, references, templates) into the conversation context.

Tool output includes a `<skill_content name="...">` block with the loaded content.

The following skills provide specialized sets of instructions for particular tasks
Invoke this tool to load a skill when a task matches one of the available skills.
""")
public class LoadSkill implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The name of skill to load")
    public String name;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        statusUpdate.accept("Loading Skill " + name);
        Optional<Skill> availableSkill = conversation.skills().stream()
                .filter(skill -> skill.name().equalsIgnoreCase(name))
                .findFirst();

        if (availableSkill.isEmpty()) {
            String availableSkills = conversation.skills()
                    .stream()
                    .map(Skill::name)
                    .collect(Collectors.joining(", "));
            if (availableSkills.isEmpty()) {
                availableSkills = "No skills are currently available.";
            } else {
                availableSkills = "Available skills: " + availableSkills;
            }
            
            return String.format("Error: Skill %s not found. %s", name, availableSkills);
        } else {
            Skill skill = availableSkill.get();
            String files = skill.references().stream()
                    .map(path -> path.relativize(skill.path()))
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
            files += skill.scripts().stream()
                    .map(path -> path.relativize(skill.path()))
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
                    
            return String.format("""
<skill_content name="%s">
# Skill: %s

%s

Base directory for this skill: %s,
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
Note: file list is sampled.

<skill_files>,
%s
</skill_files>,
</skill_content>,
""", skill.name(), skill.name(), skill.content().trim(), skill.path().toAbsolutePath(), files);
        }
    }

    /**
     * The specification for Skill tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        return new Tool.Spec(LoadSkill.class, List.of());
    }
}
