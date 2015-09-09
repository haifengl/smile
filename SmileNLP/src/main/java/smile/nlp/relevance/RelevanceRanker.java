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
    public double rank(Corpus corpus, TextTerms doc, String term, int tf, int n);

    /**
     * Returns a relevance score between a set of terms and a document based on a corpus.
     * @param corpus the corpus.
     * @param doc the document to rank.
     * @param terms the searching terms.
     * @param tf the term frequencies in the document.
     * @param n the number of documents containing the given term in the corpus;
     */
    public double rank(Corpus corpus, TextTerms doc, String[] terms, int[] tf, int n);
}