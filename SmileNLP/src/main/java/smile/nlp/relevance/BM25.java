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
 * The BM25 weighting scheme, often called Okapi weighting, after the system in
 * which it was first implemented, was developed as a way of building a
 * probabilistic model sensitive to term frequency and document length while
 * not introducing too many additional parameters into the model. It is not
 * a single function, but actually a whole family of scoring functions, with
 * slightly different components and parameters.
 * <p>
 * At the extreme values of the coefficient b, BM25 turns into ranking functions
 * known as BM11 (for b = 1) and BM15 (for b = 0). BM25F is a modification of
 * BM25 in which the document is considered to be composed from several fields
 * (such as headlines, main text, anchor text) with possibly different degrees
 * of importance.
 * <p>
 * BM25 and its newer variants represent state-of-the-art TF-IDF-like retrieval
 * functions used in document retrieval, such as web search.
 *
 * @see TFIDF
 *
 * @author Haifeng Li
 */
public class BM25 implements RelevanceRanker {

    /**
     * Free parameter, usually chosen as k1 = 2.0.
     */
    private double k1;
    /**
     * Free parameter, usually chosen as b = 0.75.
     */
    private double b;
    /**
     * The control parameter in BM25+. The standard BM25 in which the
     * component of term frequency normalization by document length
     * is not properly lower-bounded; as a result of this deficiency,
     * long documents which do match the query term can often be scored
     * unfairly by BM25 as having a similar relevance to shorter
     * documents that do not contain the query term at all. 
     */
    private double delta;
    
    /**
     * BM25F parameters
     */
    private double kf = 4.9; // k1 in BM25F
    private double bTitle = 0.6;
    private double bBody = 0.5;
    private double bAnchor = 0.6;
    private double wTitle = 13.5;
    private double wBody = 1.0;
    private double wAnchor = 11.5;
    
    /**
     * Default constructor with k1 = 1.2, b = 0.75, delta = 1.0.
     */
    public BM25() {
        this(1.2, 0.75, 1.0);
    }

    /**
     * Constructor.
     *
     * @param k1 is a positive tuning parameter that calibrates
     * the document term frequency scaling. A k1 value of 0 corresponds to a
     * binary model (no term frequency), and a large value corresponds to using
     * raw term frequency.
     * @param b b is another tuning parameter (0 &le; b &le; 1) which determines
     * the scaling by document length: b = 1 corresponds to fully scaling the
     * term weight by the document length, while b = 0 corresponds to no length
     * normalization.
     */
    public BM25(double k1, double b, double delta) {
        if (k1 < 0) {
            throw new IllegalArgumentException("Negative k1 = " + k1);
        }

        if (b < 0 || b > 1) {
            throw new IllegalArgumentException("Invalid b = " + b);
        }

        if (delta < 0) {
            throw new IllegalArgumentException("Invalid delta = " + delta);
        }

        this.k1 = k1;
        this.b = b;
        this.delta = delta;
    }

    /**
     * Returns a relevance score between a term and a document based on a corpus.
     * @param termFreq normalized term frequency of searching term in the document to rank.
     * @param N the number of documents in the corpus.
     * @param n the number of documents containing the given term in the corpus;
     */
    public double score(int termFreq, int docLen, double avgDocLen, int titleTermFreq, int titleLen, double avgTitleLen, int anchorTermFreq, int anchorLen, double avgAnchorLen, long N, long n) {
        if (termFreq <= 0) return 0.0;

        double tf = wBody * termFreq / (1.0 + bBody * (docLen / avgDocLen - 1.0));
        
        if (titleTermFreq > 0) {
        	tf += wTitle * titleTermFreq / (1.0 + bTitle * (titleLen / avgTitleLen - 1.0));
        }
        
        if (anchorTermFreq > 0) {
        	tf += wAnchor * anchorTermFreq / (1.0 + bAnchor * (anchorLen / avgAnchorLen - 1.0));
        }
        
        tf = tf / (kf + tf);
        double idf = Math.log((N - n + 0.5) / (n + 0.5));

        return (tf + delta) * idf;
    }

    /**
     * Returns a relevance score between a term and a document based on a corpus.
     * @param freq normalized term frequency of searching term in the document to rank.
     * @param N the number of documents in the corpus.
     * @param n the number of documents containing the given term in the corpus;
     */
    public double score(double freq, long N, long n) {
        if (freq <= 0) return 0.0;

        double tf = (k1 + 1) * freq / (freq + k1);
        double idf = Math.log((N - n + 0.5) / (n + 0.5));

        return (tf + delta) * idf;
    }

    /**
     * Returns a relevance score between a term and a document based on a corpus.
     * @param freq the frequency of searching term in the document to rank.
     * @param docSize the size of document to rank.
     * @param avgDocSize the average size of documents in the corpus.
     * @param N the number of documents in the corpus.
     * @param n the number of documents containing the given term in the corpus;
     */
    public double score(double freq, int docSize, double avgDocSize, long N, long n) {
        if (freq <= 0) return 0.0;

        double tf = freq * (k1 + 1) / (freq + k1 * (1 - b + b * docSize / avgDocSize));
        double idf = Math.log((N - n + 0.5) / (n + 0.5));

        return (tf + delta) * idf;
    }

    @Override
    public double rank(Corpus corpus, TextTerms doc, String term, int tf, int n) {
        if (tf <= 0) return 0.0;

        int N = corpus.getNumDocuments();
        int docSize = doc.size();
        int avgDocSize = corpus.getAverageDocumentSize();

        return score(tf, docSize, avgDocSize, N, n);
    }

    @Override
    public double rank(Corpus corpus, TextTerms doc, String[] terms, int[] tf, int n) {
        int N = corpus.getNumDocuments();
        int docSize = doc.size();
        int avgDocSize = corpus.getAverageDocumentSize();

        double r = 0.0;
        for (int i = 0; i < terms.length; i++) {
            r += score(tf[i], docSize, avgDocSize, N, n);
        }

        return r;
    }
}
