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
package smile.agent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Custom slash commands let users save and reuse their favorite or
 * most frequently used prompts as personal shortcuts. Commands are
 * essentially pre-defined prompt templates and can be triggered with
 * a simple / prefix in the chat input box. Commands may be specific
 * to a single project or are available globally across all projects
 * to ensure consistency.
 *
 * @author Haifeng Li
 */
public class Command extends Memory {
    /** The model to execute the command. */
    private final String model;
    /** argument-hint. */
    private final String hint;
    /** The tools command can use. */
    private final List<String> allowedTools = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param path the file path of the persistent memory.
     */
    public Command(String content, ObjectNode metadata, Path path) {
        super(content, metadata, path);
        var node = metadata.get("model");
        model = node != null ? node.asString() : null;

        node = metadata.get("argument-hint");
        hint = node != null ? node.asString() : null;

        node = metadata.get("allowed-tools");
        if (node instanceof ArrayNode array) {
            for (var element : array) {
                allowedTools.add(element.asString());
            }
        } else if (node.isString()) {
            allowedTools.add(node.asString());
        }
    }

    /**
     * Returns the model to execute the command. If missing, the model from
     * conversation will be used.
     * @return the model to execute the command.
     */
    public Optional<String> model() {
        return Optional.ofNullable(model);
    }

    /// Returns the expected arguments from users.
    ///
    /// **Best practices:**
    /// - Use square brackets `[]` for each argument
    /// - Use descriptive names (not `arg1`, `arg2`)
    /// - Indicate optional vs required in description
    /// - Match order to positional arguments in command
    /// - Keep concise but clear
    ///
    /// @return the expected arguments from users.
    public Optional<String> hint() {
        return Optional.ofNullable(hint);
    }

    @Override
    public String description() {
        var node = metadata.get("description");
        // If missing, returns the first line of command prompt.
        return node != null ? node.asString() : content.split("\\n", 2)[0];
    }

    /**
     * Returns the tools command can use. If empty, command will inherit all
     * available tools from conversation permissions.
     * @return the tools command can use.
     */
    public List<String> allowedTools() {
        return allowedTools;
    }

    /**
     * Reads the slash command from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the rule.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Command from(Path path) throws IOException {
        Memory memory = Memory.from(path);
        return new Command(memory.content(), memory.metadata(), path);
    }
}
