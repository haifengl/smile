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
 * A sentence splitter based on the java.text.BreakIterator, which supports
 * multiple natural languages (selected by locale setting).
 * 
 * @author Haifeng Li
 */
public class BreakIteratorSentenceSplitter implements SentenceSplitter {

    /**
     * The working horse for splitting sentences.
     */
    private BreakIterator boundary;

    /**
     * Constructor for the default locale.
     */
    public BreakIteratorSentenceSplitter() {
        boundary = BreakIterator.getSentenceInstance();
    }

    /**
     * Constructor for the given locale.
     */
    public BreakIteratorSentenceSplitter(Locale locale) {
        boundary = BreakIterator.getSentenceInstance(locale);
    }

    @Override
    public String[] split(String text) {
        boundary.setText(text);
        ArrayList<String> sentences = new ArrayList<>();
        int start = boundary.first();
        for (int end = boundary.next();
                end != BreakIterator.DONE;
                start = end, end = boundary.next()) {
            sentences.add(text.substring(start, end).trim());
        }

        String[] array = new String[sentences.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = sentences.get(i);
        }

        return array;
    }
}
