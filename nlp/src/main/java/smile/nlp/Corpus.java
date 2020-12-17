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
     * @return the number of words in the corpus.
     */
    long size();
    
    /**
     * Returns the number of documents in the corpus.
     * @return the number of documents in the corpus.
     */
    int ndoc();

    /**
     * Returns the number of unique terms in the corpus.
     * @return the number of unique terms in the corpus.
     */
    int nterm();

    /**
     * Returns the number of bigrams in the corpus.
     * @return the number of bigrams in the corpus.
     */
    long nbigram();

    /**
     * Returns the average size of documents in the corpus.
     * @return the average size of documents in the corpus.
     */
    int avgDocSize();

    /**
     * Returns the total frequency of the term in the corpus.
     * @param term the term.
     * @return the total frequency of the term in the corpus.
     */
    int count(String term);

    /**
     * Returns the total frequency of the bigram in the corpus.
     * @param bigram the bigram.
     * @return the total frequency of the bigram in the corpus.
     */
    int count(Bigram bigram);

    /**
     * Returns the iterator over the terms in the corpus.
     * @return the iterator of terms.
     */
    Iterator<String> terms();

    /**
     * Returns the iterator over the bigrams in the corpus.
     * @return the iterator of bigrams.
     */
    Iterator<Bigram> bigrams();

    /**
     * Returns the iterator over the set of documents containing the given term.
     *
     * @param term the search term.
     * @return the iterator of documents containing the term.
     */
    Iterator<Text> search(String term);

    /**
     * Returns the iterator over the set of documents containing
     * the given term in descending order of relevance.
     *
     * @param ranker the relevance ranker.
     * @param term the search term.
     * @return the iterator of documents in descending order of relevance.
     */
    Iterator<Relevance> search(RelevanceRanker ranker, String term);

    /**
     * Returns the iterator over the set of documents containing
     * (at least one of) the given terms in descending order of
     * relevance.
     *
     * @param ranker the relevance ranker.
     * @param terms the search terms.
     * @return the iterator of documents in descending order of relevance.
     */
    Iterator<Relevance> search(RelevanceRanker ranker, String[] terms);
}
