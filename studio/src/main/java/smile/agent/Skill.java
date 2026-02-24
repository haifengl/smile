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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.node.ObjectNode;

/**
 * Skills are modular, specialized, and reusable workflows
 * that an agent activates on-demand when relevant, acting
 * like "optional expertise". Skills enhance productivity
 * by automating specific tasks without manual prompting.
 *
 * @author Haifeng Li
 */
public class Skill extends Memory {
    /**
     * Optional executable scripts (fill_form.py, validate.jsh, test.java)
     * that run via bash; scripts provide deterministic operations without
     * consuming context.
     */
    private final Map<String, Path> scripts;
    /**
     * Optional reference documents.
     */
    private final Map<String, Path> references;

    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param scripts the executable scripts that run via bash.
     * @param path the file path of the skill folder.
     */
    public Skill(String content,
                 ObjectNode metadata,
                 Map<String, Path> scripts,
                 Map<String, Path> references,
                 Path path) {
        super(content, metadata, path);
        this.scripts = scripts;
        this.references = references;
    }

    /**
     * Returns the license of the skill.
     * @return the license of the skill.
     */
    public String license() {
        var node = metadata.get("license");
        return node != null ? node.asString() : "";
    }

    /**
     * Returns the compatibility of the skill.
     * @return the compatibility of the skill.
     */
    public String compatibility() {
        var node = metadata.get("compatibility");
        return node != null ? node.asString() : "";
    }

    /**
     * Reads the skill from a folder with UTF-8 charset.
     * @param path the path to the skill folder.
     * @return the skill.
     * @throws IOException if an I/O error occurs reading from the files.
     */
    public static Skill from(Path path) throws IOException {
        Memory memory = Memory.from(path.resolve("SKILL.md"));
        Map<String, Path> scripts = Map.of();
        var scriptDir = path.resolve("scripts");
        if (Files.exists(scriptDir)) {
            try (var stream = Files.list(scriptDir)) {
                scripts = stream.filter(Files::isRegularFile)
                        .filter(Files::isExecutable)
                        .collect(
                                Collectors.toMap(
                                        f -> f.getFileName().toString(),
                                        f -> f
                                )
                        );
            }
        }

        Map<String, Path> references = Map.of();
        var refDir = path.resolve("references");
        if (Files.exists(refDir)) {
            try (var stream = Files.list(refDir)) {
                references = stream.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".md"))
                        .collect(
                                Collectors.toMap(
                                        f -> f.getFileName().toString(),
                                        f -> f
                                )
                        );
            }
        }
        return new Skill(memory.content(), memory.metadata(), scripts, references, path);
    }
}
