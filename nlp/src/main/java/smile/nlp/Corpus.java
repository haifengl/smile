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

package smile.nlp;

import java.util.Iterator;
import smile.nlp.relevance.Relevance;
import smile.nlp.relevance.RelevanceRanker;

/**
 * A corpus is a collection of documents.
 *
 * @author Haifeng Li
 */
public interface Corpus {

    /**
     * Returns the number of words in the corpus.
     */
    long size();
    
    /**
     * Returns the number of documents in the corpus.
     */
    int getNumDocuments();

    /**
     * Returns the number of unique terms in the corpus.
     */
    int getNumTerms();

    /**
     * Returns the number of bigrams in the corpus.
     */
    long getNumBigrams();

    /**
     * Returns the average size of documents in the corpus.
     */
    int getAverageDocumentSize();

    /**
     * Returns the total frequency of the term in the corpus.
     */
    int getTermFrequency(String term);

    /**
     * Returns the total frequency of the bigram in the corpus.
     */
    int getBigramFrequency(Bigram bigram);

    /**
     * Returns an iterator over the terms in the corpus.
     */
    Iterator<String> getTerms();

    /**
     * Returns an iterator over the bigrams in the corpus.
     */
    Iterator<Bigram> getBigrams();

    /**
     * Returns an iterator over the set of documents containing the given term.
     */
    Iterator<Text> search(String term);

    /**
     * Returns an iterator over the set of documents containing the given term
     * in descending order of relevance.
     */
    Iterator<Relevance> search(RelevanceRanker ranker, String term);

    /**
     * Returns an iterator over the set of documents containing (at least one
     * of) the given terms in descending order of relevance.
     */
    Iterator<Relevance> search(RelevanceRanker ranker, String[] terms);
}
