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

package smile.nlp.relevance;

import smile.nlp.Corpus;
import smile.nlp.TextTerms;

/**
 * An interface to provide relevance ranking algorithm.
 *
 * @author Haifeng Li
 */
public interface RelevanceRanker {
    /**
     * Returns a relevance score between a term and a document based on a corpus.
     * @param corpus the corpus.
     * @param doc the document to rank.
     * @param term the searching term.
     * @param tf the term frequency in the document.
     * @param n the number of documents containing the given term in the corpus;
     */
    double rank(Corpus corpus, TextTerms doc, String term, int tf, int n);

    /**
     * Returns a relevance score between a set of terms and a document based on a corpus.
     * @param corpus the corpus.
     * @param doc the document to rank.
     * @param terms the searching terms.
     * @param tf the term frequencies in the document.
     * @param n the number of documents containing the given term in the corpus;
     */
    double rank(Corpus corpus, TextTerms doc, String[] terms, int[] tf, int n);
}