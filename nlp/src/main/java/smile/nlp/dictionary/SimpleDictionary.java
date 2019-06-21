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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A simple implementation of dictionary interface.
 *
 * @author Haifeng Li
 */
public class SimpleDictionary implements Dictionary {

    /**
     * A list of abbreviations.
     */
    private HashSet<String> dict;

    /**
     * Constructor.
     * @param resource the file name of dictionary. The file should be in plain
     * text, in which each line is a word.
     */
    public SimpleDictionary(String resource) {
        dict = new HashSet<>();

        File file = new File(resource);
        try (BufferedReader input = file.exists() ?
             new BufferedReader(new FileReader(resource)) :
             new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(resource)))) {
            
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
