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
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.agent.Command;
import smile.llm.Conversation;
import smile.llm.client.ResponseHandler;

@JsonClassDescription("""
Execute a slash command within the main conversation

**IMPORTANT - Intent Matching:**
Before starting any task, CHECK if the user's request matches one of the slash commands listed below. This tool exists to route user intentions to specialized workflows.

How slash commands work:
When you use this tool or when a user types a slash command, you will see <command-message>{name} is running…</command-message> followed by the expanded prompt. For example, if .smile/agent/commands/foo.md contains "Print today's date", then /foo expands to that prompt in the next message.

Usage:
- `command` (required): The slash command to execute, including any arguments
- Example: `command: "/review-pr 123"`

IMPORTANT: Only use this tool for custom slash commands that appear in the Available Commands list below. Do NOT use for:
- Built-in CLI commands (like /help, /clear, etc.)
- Commands not shown in the list
- Commands you think might exist but aren't listed

Notes:
- When a user requests multiple slash commands, execute each one sequentially and check for <command-message>{name} is running…</command-message> to verify each has been processed
- Do not invoke a command that is already running. For example, if you see <command-message>foo is running…</command-message>, do NOT use this tool with "/foo" - process the expanded prompt in the following message
- Only custom slash commands with descriptions are listed in Available Commands. If a user's command is not listed, ask them to check the slash command file and consult the docs.
""")
public class SlashCommand implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The slash command to execute with its arguments, e.g., \"/review-pr 123\"")
    public String command;

    @Override
    public String run(Conversation conversation, ResponseHandler handler) {
        handler.onStatus("Running " + command);
        if (!command.startsWith("/")) {
            return "Error: Invalid command format. Command must start with '/'.";
        }

        // Remove the leading '/'
        String name = command.split("\\s+")[0].substring(1);
        Optional<Command> availableCommand = conversation.commands().stream()
                .filter(skill -> skill.name().equalsIgnoreCase(name))
                .findFirst();

        if (availableCommand.isEmpty()) {
            String availableCommands = conversation.commands()
                    .stream()
                    .map(Command::name)
                    .map(s -> "/" + s) // Prepend '/' to command names
                    .collect(Collectors.joining(", "));
            if (availableCommands.isEmpty()) {
                availableCommands = "No custom commands are currently available.";
            } else {
                availableCommands = "Available custom commands: " + availableCommands;
            }

            return String.format("Error: Command /%s not found. %s", name, availableCommands);
        } else {
            Command cmd = availableCommand.get();
            var args = command.substring(name.length()+1).trim();

            String prompt;
            if (cmd.content().contains("{{args}}")) {
                if (args.isBlank()) {
                    return "Error: /" + name + " requires arguments.";
                } else {
                    prompt = cmd.prompt(args);
                }
            } else {
                // append instructions to command without {{args}} placeholder.
                prompt = cmd.content() + "\n\n" + args;
            }

            return String.format("""
"Please execute the following instructions precisely:
""\"
%s
""\"
""", prompt);
        }
    }

    /**
     * The specification for SlashCommand tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        return new Tool.Spec(SlashCommand.class, List.of());
    }
}
