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
    public long size();
    
    /**
     * Returns the number of documents in the corpus.
     */
    public int getNumDocuments();

    /**
     * Returns the number of unique terms in the corpus.
     */
    public int getNumTerms();

    /**
     * Returns the number of bigrams in the corpus.
     */
    public long getNumBigrams();

    /**
     * Returns the average size of documents in the corpus.
     */
    public int getAverageDocumentSize();

    /**
     * Returns the total frequency of the term in the corpus.
     */
    public int getTermFrequency(String term);

    /**
     * Returns the total frequency of the bigram in the corpus.
     */
    public int getBigramFrequency(Bigram bigram);

    /**
     * Returns an iterator over the terms in the corpus.
     */
    public Iterator<String> getTerms();

    /**
     * Returns an iterator over the bigrams in the corpus.
     */
    public Iterator<Bigram> getBigrams();

    /**
     * Returns an iterator over the set of documents containing the given term.
     */
    public Iterator<Text> search(String term);

    /**
     * Returns an iterator over the set of documents containing the given term
     * in descending order of relevance.
     */
    public Iterator<Relevance> search(RelevanceRanker ranker, String term);

    /**
     * Returns an iterator over the set of documents containing (at least one
     * of) the given terms in descending order of relevance.
     */
    public Iterator<Relevance> search(RelevanceRanker ranker, String[] terms);
}
