/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.nlp;

/**
 * The terms in a text.
 *
 * @author Haifeng Li
 */
public interface TextTerms {
    
    /**
     * Returns the number of words.
     * @return the number of words.
     */
    int size();

    /**
     * Returns the iterator of the words of the document.
     * The stop words and punctuations may be removed.
     * @return the iterator of the words of the document.
     */
    Iterable<String> words();

    /**
     * Returns the iterator of unique words.
     * @return the iterator of unique words.
     */
    Iterable<String> unique();

    /**
     * Returns the term frequency.
     * @param term the term.
     * @return the term frequency.
     */
    int tf(String term);

    /**
     * Returns the maximum term frequency over all terms in the document.
     * @return the maximum term frequency.
     */
    int maxtf();

}
