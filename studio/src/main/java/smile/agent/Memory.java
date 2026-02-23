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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Long-term memory stores instructions, rules, and preferences
 * for agents to follow. LLMs are stateless functions. To maintain
 * context across interactions, we need to externalize memory. Long-term
 * memory is persistent and can be retrieved and updated over time.
 *
 * @author Haifeng Li
 */
public class Memory {
    /**
     * The main content, which may be in structured format
     * such as Markdown for readability and simplicity.
     */
    final String content;
    /**
     * The metadata of memory.
     */
    final ObjectNode metadata;
    /**
     * The file path of the persistent memory.
     */
    final Path path;

    /**
     * Constructor.
     *
     * @param content the main content, which may be in structured format
     *                such as Markdown for readability and simplicity.
     * @param metadata the metadata of memory.
     * @param path the file path of the persistent memory.
     */
    public Memory(String content, ObjectNode metadata, Path path) {
        this.content = content;
        this.metadata = metadata;
        this.path = path;
    }

    /**
     * Returns the main content of the memory.
     * @return the main content of the memory.
     */
    public String content() {
        return content;
    }

    /**
     * Returns the metadata of the memory as key-value pairs.
     * @return the metadata of the memory as key-value pairs.
     */
    public ObjectNode metadata() {
        return metadata;
    }

    /**
     * Returns the file path of the persistent memory.
     * @return the file path of the persistent memory.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the name of the memory.
     * @return the name of the memory.
     */
    public String name() {
        var node = metadata.get("name");
        return node != null ? node.asString() : path.getFileName().toString();
    }

    /**
     * Returns the description of the memory.
     * @return the description of the memory.
     */
    public String description() {
        var node = metadata.get("description");
        return node != null ? node.asString() : "";
    }

    /**
     * Reads the persistent memory from a file with UTF-8 charset.
     * The file may have metadata placed at the top in YAML front
     * matter, delimited by lines of at least three dashes (---).
     * The main content follows the front matter.
     * @param path the path to the file.
     * @return the specification.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Memory from(Path path) throws IOException {
        var lines = Files.readAllLines(path);
        ObjectMapper mapper = new YAMLMapper();
        ObjectNode metadata = mapper.createObjectNode();

        // detect YAML front matter
        int i = 0;
        String line = lines.getFirst();
        // use at least three dashes
        if (line.matches("[-]{3,}")) {
            StringBuilder sb = new StringBuilder();
            for (i = 1; i < lines.size(); i++) {
                line = lines.get(i);
                if (line.matches("[-]{3,}")) {
                    break;
                }
                sb.append(line);
                sb.append("\n");
            }

            metadata = (ObjectNode) mapper.readTree(sb.toString());
        }

        StringBuilder sb = new StringBuilder();
        for (i++; i < lines.size(); i++) {
            sb.append(lines.get(i));
            sb.append("\n");
        }
        String content = sb.toString();
        final String delimiter = line;

        return new Memory(content, metadata, path);
    }

    /**
     * Saves the memory to the file path specified in the memory.
     * @throws IOException if an I/O error occurs writing to the file.
     */
    public void save() throws IOException  {
        writeTo(path);
    }

    /**
     * Writes the memory to a file with UTF-8 charset.
     * The metadata is stored at the top in YAML front matter,
     * delimited by lines of at least three dashes (---).
     * The main content follows the front matter.
     * @param path the path to the file.
     * @throws IOException if an I/O error occurs writing to the file.
     */
    public void writeTo(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (metadata != null && !metadata.isEmpty()) {
            ObjectMapper mapper = new YAMLMapper();
            sb.append(mapper.writeValueAsString(metadata));
            sb.append("---\n");
        }
        sb.append(content);
        Files.writeString(path, sb.toString());
    }
}
