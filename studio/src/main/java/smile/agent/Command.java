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
import java.util.Map;

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
    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param path the file path of the persistent memory.
     */
    public Command(String content, Map<String, String> metadata, Path path) {
        super(content, metadata, path);
    }

    /**
     * Generates the prompt by replacing the {{args}} placeholder
     * in the content with the provided arguments.
     * @param args the arguments to replace in the content.
     * @return the generated prompt.
     */
    public String prompt(String args) {
        return content.replace("{{args}}", args);
    }

    /**
     * Reads the rule from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the rule.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Command from(Path path) throws IOException {
        Memory memory = Memory.from(path);
        return new Command(memory.content(), memory.metadata(), path);
    }
}
