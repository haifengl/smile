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
