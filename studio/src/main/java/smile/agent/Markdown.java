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
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Markdown represents a Markdown file with optional YAML front matter.
 * The front matter is delimited by lines of at least three dashes (---).
 * The content is the Markdown text following the front matter.
 *
 * @param content the Markdown content without the front matter.
 * @param frontMatter the key-value pairs from the YAML front matter,
 *                    or null if no front matter is present.
 *
 * @author Haifeng Li
 */
public record Markdown(String content, Map<String, String> frontMatter) {
    /**
     * Reads the specification from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the specification.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    static Markdown from(Path path) throws IOException {
        var lines = Files.readAllLines(path);
        Map<String, String> frontMatter = null;

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

            ObjectMapper mapper = new YAMLMapper();
            frontMatter = mapper.readValue(
                    sb.toString(),
                    TypeFactory.createDefaultInstance().constructMapType(HashMap.class, String.class, String.class)
            );
        }

        StringBuilder sb = new StringBuilder();
        for (i++; i < lines.size(); i++) {
            sb.append(lines.get(i));
            sb.append("\n");
        }
        String content = sb.toString();
        final String delimiter = line;

        return new Markdown(content, frontMatter);
    }

    /**
     * Writes the Markdown content and front matter to a file with UTF-8 charset.
     * @param path the path to the file.
     * @throws IOException if an I/O error occurs writing to the file.
     */
    public void writeTo(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (frontMatter != null) {
            sb.append("---\n");
            ObjectMapper mapper = new YAMLMapper();
            sb.append(mapper.writeValueAsString(frontMatter));
            sb.append("---\n");
        }
        sb.append(content);
        Files.writeString(path, sb.toString());
    }
}
