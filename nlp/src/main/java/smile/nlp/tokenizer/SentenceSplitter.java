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
package smile.nlp.tokenizer;

/**
 * A sentence splitter segments text into sentences (a string of words
 * satisfying the grammatical rules of a language).
 *
 * @author Haifeng Li
 */
public interface SentenceSplitter {
    /**
     * Splits the text into sentences.
     * @param text the text.
     * @return the sentences.
     */
    String[] split(String text);
}
