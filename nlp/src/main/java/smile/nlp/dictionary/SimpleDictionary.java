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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

/**
 * A simple implementation of dictionary interface.
 *
 * @author Haifeng Li
 */
public class SimpleDictionary implements Dictionary {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SimpleDictionary.class);

    /**
     * A set of words in this dictionary.
     */
    private final HashSet<String> dict;

    /**
     * Constructor that loads a dictionary from a file path or classpath resource.
     * If the given path points to an existing file, it is loaded directly.
     * Otherwise, it is treated as a classpath resource path.
     *
     * @param resource the file path or classpath resource path of the dictionary.
     *                 The file should be plain text with one word per line.
     */
    public SimpleDictionary(String resource) {
        dict = new HashSet<>();
        Path path = Path.of(resource);
        try (BufferedReader input = Files.exists(path) ?
             Files.newBufferedReader(path) :
             new BufferedReader(new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream(resource))))) {
            load(input);
        } catch (IOException ex) {
            logger.error("Failed to parse dictionary: {}", resource, ex);
        }
    }

    /**
     * Constructor that loads a dictionary from a {@link Path}.
     *
     * @param path the path to the dictionary file. The file should be plain
     *             text with one word per line.
     */
    public SimpleDictionary(Path path) {
        dict = new HashSet<>();
        try (BufferedReader input = Files.newBufferedReader(path)) {
            load(input);
        } catch (IOException ex) {
            logger.error("Failed to parse dictionary: {}", path, ex);
        }
    }

    /**
     * Loads words from a reader into the dictionary set.
     */
    private void load(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Skip blank lines and single capital letters.
            if (!line.isEmpty() && !line.matches("^[A-Z]$")) {
                dict.add(line);
            }
        }
    }

    @Override
    public boolean contains(String word) {
        return dict.contains(word);
    }

    @Override
    public int size() {
        return dict.size();
    }

    @Override
    public Iterator<String> iterator() {
        return dict.iterator();
    }
}
