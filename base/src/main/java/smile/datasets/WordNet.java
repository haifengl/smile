/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import smile.io.Paths;

/**
 * Words in WordNet datasets. WordNet is a lexical database of semantic
 * relations between words that links words into semantic relations
 * including synonyms, hyponyms, and meronyms.
 *
 * @param words the nouns.
 * @author Haifeng Li
 */
public record WordNet(String[] words) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public WordNet() throws IOException {
        this(Paths.getTestData("text/index.noun"));
    }

    /**
     * Constructor.
     * @param path the path to WordNet's index file (e.g., index.noun).
     * @throws IOException when fails to read the file.
     */
    public WordNet(Path path) throws IOException {
        this(load(path));
    }

    private static String[] load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.lines().filter(line -> !line.startsWith(" "))
                    .map(line -> line.split("\\s")[0].replace('_', ' '))
                    .toArray(String[]::new);
        }
    }
}
