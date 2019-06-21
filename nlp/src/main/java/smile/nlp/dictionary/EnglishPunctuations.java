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

import java.util.HashSet;
import java.util.Iterator;

/**
 * Punctuation marks in English.
 * 
 * @author Haifeng Li
 */
public class EnglishPunctuations implements Punctuations {
    /**
     * The singleton instance.
     */
    private static EnglishPunctuations singleton = new EnglishPunctuations();
    /**
     * A set of punctuation marks.
     */
    private HashSet<String> dict = new HashSet<>(50);

    /**
     * Constructor.
     */
    private EnglishPunctuations() {
        dict.add("[");
        dict.add("]");
        dict.add("(");
        dict.add(")");
        dict.add("{");
        dict.add("}");
        dict.add("<");
        dict.add(">");
        dict.add(":");
        dict.add(",");
        dict.add(";");
        dict.add("-");
        dict.add("--");
        dict.add("---");
        dict.add("!");
        dict.add("?");
        dict.add(".");
        dict.add("...");
        dict.add("`");
        dict.add("'");
        dict.add("\"");
        dict.add("/");
    }

    /**
     * Returns the singleton instance.
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
