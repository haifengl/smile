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

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Rules are mandatory, context-independent instructions
 * that apply to every interaction for consistency.
 * Rules are best for behavioral constraints and formatting
 * standards. Each rule is a Markdown file with frontmatter
 * metadata and content.
 *
 * @author Haifeng Li
 */
public class Rule extends Memory {
    /** If true, forces the rule to be applied regardless of the context. */
    private final boolean alwaysApply;
    /** The file patterns where this rule applies. */
    private final List<String> globs;

    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param path the file path of the persistent memory.
     */
    public Rule(String content, ObjectNode metadata, Path path) {
        super(content, metadata, path);

        var node = metadata.get("alwaysApply");
        alwaysApply = node != null && node.asBoolean();

        node = metadata.get("globs");
        if (node == null) node = metadata.get("paths");
        if (node == null) {
            globs = List.of();
        } else if (node instanceof ArrayNode array) {
            List<String> list = new ArrayList<>();
            for (var element : array) {
                list.add(element.asString());
            }
            globs = list;
        } else {
            globs = List.of(node.asString());
        }
    }

    /**
     * Returns the file patterns where this rule applies.
     * @return the file patterns.
     */
    public List<String> globs() {
        return globs;
    }

    /**
     * Returns a boolean that, if true, forces the rule to be
     * applied regardless of the context.
     * @return the flag if enforcing the rule.
     */
    public boolean alwaysApply() {
        return alwaysApply;
    }

    /**
     * Reads the rule from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the rule.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Rule from(Path path) throws IOException {
        Memory memory = Memory.from(path);
        return new Rule(memory.content(), memory.metadata(), path);
    }
}
