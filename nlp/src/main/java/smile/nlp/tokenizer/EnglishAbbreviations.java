/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.nlp.tokenizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A concise dictionary of common abbreviations (e.g. Mr., Mrs., etc.) in English.
 * Useful in sentence splitter and word tokenizer.
 *
 * @author Haifeng Li
 */
class EnglishAbbreviations {
    /** Utility classes should not have public constructors. */
    private EnglishAbbreviations() {

    }

    /**
     * A list of abbreviations.
     */
    private static final HashSet<String> DICTIONARY;

    static {
        DICTIONARY = new HashSet<>();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(EnglishAbbreviations.class.getResourceAsStream("/smile/nlp/tokenizer/abbreviations_en.txt")))) {
            String line = null;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    DICTIONARY.add(line);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Returns true if this abbreviation dictionary contains the specified element.
     */
    public static boolean contains(String s) {
        return DICTIONARY.contains(s);
    }

    /**
     * Returns the number of elements in this abbreviation dictionary.
     */
    public static int size() {
        return DICTIONARY.size();
    }

    /**
     * Returns an iterator over the elements in this abbreviation dictionary.
     */
    public static Iterator<String> iterator() {
        return DICTIONARY.iterator();
    }
}
