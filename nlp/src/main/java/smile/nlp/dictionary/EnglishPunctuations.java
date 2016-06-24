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
