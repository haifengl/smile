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
package smile.nlp.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;

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
    private HashSet<String> dict;

    /**
     * Constructor.
     * @param resource the file name of dictionary. The file should be in plain
     * text, in which each line is a word.
     */
    private EnglishDictionary(String resource) {
        dict = new HashSet<>();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(resource)))) {
        
            String line = null;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                // Remove blank line or single capital characters from dictionary.
                if (!line.isEmpty() && !line.matches("^[A-Z]$")) {
                    dict.add(line);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
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
