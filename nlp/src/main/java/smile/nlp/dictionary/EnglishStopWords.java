/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.nlp.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;

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
    private HashSet<String> dict;

    /**
     * Constructor.
     */
    EnglishStopWords(String resource) {
        dict = new HashSet<>();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(resource)))) {
            String line = null;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    dict.add(line);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
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
