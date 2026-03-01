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

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Specs (Specifications) are the documents that define the requirements,
 * intent, data structure, metadata, and statistics, serving
 * as the blueprint for the AI to follow. Specs turn vague requests
 * into precise instructions, reducing hallucinations and ensuring
 * code quality. Each spec is a Markdown file.
 *
 * @author Haifeng Li
 */
public class Spec extends Memory {
    /** If true, forces the spec to be applied regardless of the context. */
    final boolean alwaysApply;
    /** The file patterns where this spec applies. */
    final List<String> globs = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param content  the main content, which may be in structured format
     *                 such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param path     the file path of the persistent memory.
     */
    public Spec(String content, ObjectNode metadata, Path path) {
        super(content, metadata, path);

        var node = metadata.get("globs");
        if (node == null) node = metadata.get("paths");
        if (node instanceof ArrayNode array) {
            for (var element : array) {
                globs.add(element.asString());
            }
        } else if (node.isString()) {
            globs.add(node.asString());
        }

        node = metadata.get("alwaysApply");
        alwaysApply = node != null && node.asBoolean();
    }

    /**
     * Returns the file patterns where this rule applies.
     *
     * @return the file patterns.
     */
    public List<String> globs() {
        return globs;
    }

    /**
     * Returns a boolean that, if true, forces the rule to be
     * applied regardless of the context.
     *
     * @return the flag if enforcing the rule.
     */
    public boolean alwaysApply() {
        return alwaysApply;
    }

    /**
     * Reads the spec from a file with UTF-8 charset.
     *
     * @param path the path to the file.
     * @return the spec.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Spec from(Path path) throws IOException {
        Memory memory = Memory.from(path);
        if (memory.metadata().get("name") == null) {
            throw new IOException("The 'name' field is missing in spec " + path);
        }

        if (memory.metadata().get("description") == null) {
            throw new IOException("The 'description' field is missing in spec " + path);
        }

        return new Spec(memory.content(), memory.metadata(), path);
    }
}
