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

/**
 * Specs (Specifications) are the documents that define the requirements,
 * intent, data structure, metadata, and statistics, serving
 * as the blueprint for the AI to follow. Specs turn vague requests
 * into precise instructions, reducing hallucinations and ensuring
 * code quality. Each spec is a Markdown file.
 *
 * @author Haifeng Li
 */
public record Spec(String content) {
    /**
     * Reads the specification from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the specification.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Spec from(Path path) throws IOException {
        String content = Files.readString(path);
        return new Spec(content);
    }
}
