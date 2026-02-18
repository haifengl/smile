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
 * Skills are modular, specialized, and reusable workflows
 * that an agent activates on-demand when relevant, acting
 * like "optional expertise". Skills enhance productivity
 * by automating specific tasks without manual prompting.
 *
 * @author Haifeng Li
 */
public class Skill extends Memory {
    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of content as key-value pairs.
     * @param path the file path of the persistent memory.
     */
    public Skill(String content, Map<String, String> metadata, Path path) {
        super(content, metadata, path);
    }

    /**
     * Reads the skill from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the skill.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Skill from(Path path) throws IOException {
        Memory memory = Memory.from(path);
        return new Skill(memory.content(), memory.metadata(), path);
    }
}
