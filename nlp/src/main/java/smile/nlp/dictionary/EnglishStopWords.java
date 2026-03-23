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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

/**
 * Several sets of English stop words.
 *
 * @author Haifeng Li
 */
public enum EnglishStopWords implements StopWords {

    /**
     * Default stop words list.
     */
    DEFAULT("/smile/nlp/dictionary/stop-words_en.txt"),
    /**
     * A very long list of stop words.
     */
    COMPREHENSIVE("/smile/nlp/dictionary/stop-words_en_more.txt"),
    /**
     * The stop words list used by Google.
     */
    GOOGLE("/smile/nlp/dictionary/stop-words_en_google.txt"),
    /**
     * The stop words list used by MySQL FullText feature.
     */
    MYSQL("/smile/nlp/dictionary/stop-words_en_mysql.txt");
    
    /**
     * A set of stop words.
     */
    private final HashSet<String> dict;

    /**
     * Constructor.
     */
    EnglishStopWords(String resource) {
        dict = new HashSet<>();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream(resource))))) {
            String line;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    dict.add(line);
                }
            }
        } catch (IOException ex) {
            final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnglishStopWords.class);
            logger.error("Failed to load English stop words", ex);
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
