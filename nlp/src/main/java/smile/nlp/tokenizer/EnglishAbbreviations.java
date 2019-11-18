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

package smile.nlp.tokenizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A concise dictionary of common abbreviations (e.g. Mr., Mrs., etc.) in English.
 * Useful in sentence splitter and word tokenizer.
 *
 * @author Haifeng Li
 */
interface EnglishAbbreviations {
    /**
     * A list of abbreviations.
     */
    Set<String> dictionary = dictionary();

    static Set<String> dictionary() {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(EnglishAbbreviations.class.getResourceAsStream("/smile/nlp/tokenizer/abbreviations_en.txt")))) {
            return input.lines().map(line -> line.trim()).filter(line -> !line.isEmpty()).collect(Collectors.toSet());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return Collections.emptySet();
    }

    /**
     * Returns true if this abbreviation dictionary contains the specified element.
     */
    static boolean contains(String s) {
        return dictionary.contains(s);
    }

    /**
     * Returns the number of elements in this abbreviation dictionary.
     */
    static int size() {
        return dictionary.size();
    }

    /**
     * Returns an iterator over the elements in this abbreviation dictionary.
     */
    static Iterator<String> iterator() {
        return dictionary.iterator();
    }
}
