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

import java.util.Iterator;
import java.util.Set;

/**
 * Punctuation marks in English.
 * 
 * @author Haifeng Li
 */
public class EnglishPunctuations implements Punctuations {
    /**
     * The singleton instance.
     */
    private static final EnglishPunctuations singleton = new EnglishPunctuations();
    /**
     * A set of punctuation marks.
     */
    private final Set<String> dict;

    /**
     * Constructor.
     */
    private EnglishPunctuations() {
        dict = Set.of(
            "[", "]", "(", ")", "{", "}", "<", ">", ":",
            ",", ";", "-", "--", "---", "!", "?", ".",
            "...", "`", "'", "\"", "/"
        );
    }

    /**
     * Returns the singleton instance.
     * @return the singleton instance.
     */
    public static EnglishPunctuations getInstance() {
        return singleton;
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
