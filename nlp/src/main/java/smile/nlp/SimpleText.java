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
package smile.nlp;

import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;
import smile.sort.QuickSort;
import smile.util.Trie;

import java.util.*;

/**
 * A text with a unique id in the corpus.
 *
 * @param title the optional title of text.
 * @param content the text content.
 * @author Haifeng Li
 */
record SimpleText(String title, String content) implements Text {
    /**
     * Constructor.
     * @param content the text content.
     */
    public SimpleText(String content) {
        this("", content);
    }
}
