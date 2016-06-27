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

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A word tokenizer based on the java.text.BreakIterator, which supports
 * multiple natural languages (selected by locale setting).
 *
 * @author Haifeng Li
 */
public class BreakIteratorTokenizer implements Tokenizer {

    /**
     * The working horse for splitting words.
     */
    private BreakIterator boundary;

    /**
     * Constructor for the default locale.
     */
    public BreakIteratorTokenizer() {
        boundary = BreakIterator.getWordInstance();
    }

    /**
     * Constructor for the given locale.
     */
    public BreakIteratorTokenizer(Locale locale) {
        boundary = BreakIterator.getWordInstance(locale);
    }

    @Override
    public String[] split(String text) {
        boundary.setText(text);
        ArrayList<String> words = new ArrayList<>();
        int start = boundary.first();
        int end = boundary.next();

        while (end != BreakIterator.DONE) {
            String word = text.substring(start, end).trim();
            if (!word.isEmpty()) {
                words.add(word);
            }
            start = end;
            end = boundary.next();
        }

        String[] array = new String[words.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = words.get(i);
        }

        return array;
    }
}
