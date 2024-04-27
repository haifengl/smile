/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

/**
 * A concise dictionary of common terms in English.
 *
 * @author Haifeng Li
 */
public enum EnglishDictionary implements Dictionary {
    /**
     * A concise dictionary of common terms in English.
     */
    CONCISE("/smile/nlp/dictionary/dictionary_en.txt");

    /**
     * A list of abbreviations.
     */
    private final HashSet<String> dict;

    /**
     * Constructor.
     * @param resource the file name of dictionary. The file should be in plain
     * text, in which each line is a word.
     */
    EnglishDictionary(String resource) {
        dict = new HashSet<>();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream(resource))))) {
        
            String line;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                // Remove blank line or single capital characters from dictionary.
                if (!line.isEmpty() && !line.matches("^[A-Z]$")) {
                    dict.add(line);
                }
            }
        } catch (IOException ex) {
            final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnglishDictionary.class);
            logger.error("Failed to load English dictionary", ex);
        }
    }

    @Override
    public boolean contains(String s) {
        return dict.contains(s);
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
