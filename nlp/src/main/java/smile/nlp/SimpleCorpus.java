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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import smile.nlp.dictionary.EnglishPunctuations;
import smile.nlp.dictionary.EnglishStopWords;
import smile.nlp.dictionary.Punctuations;
import smile.nlp.dictionary.StopWords;
import smile.nlp.relevance.Relevance;
import smile.nlp.relevance.RelevanceRanker;
import smile.nlp.tokenizer.SentenceSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;
import smile.nlp.tokenizer.Tokenizer;

/**
 * A simple implementation of corpus in main memory for small datasets.
 *
 * @author Haifeng Li
 */
public class SimpleCorpus implements Corpus {

    /**
     * The number of terms in the corpus.
     */
    private long size;
    /**
     * The set of documents.
     */
    private List<SimpleText> docs = new ArrayList<>();
    /**
     * Frequency of single tokens.
     */
    private HashMap<String, Integer> freq = new HashMap<>();
    /**
     * Frequency of bigrams.
     */
    private HashMap<Bigram, Integer> freq2 = new HashMap<>();
    /**
     * Inverted file storing a mapping from terms to the documents containing it.
     */
    private HashMap<String, List<SimpleText>> invertedFile = new HashMap<>();
    /**
     * Sentence splitter.
     */
    private SentenceSplitter splitter;
    /**
     * Tokenizer.
     */
    private Tokenizer tokenizer;
    /**
     * The set of stop words.
     */
    private StopWords stopWords;

    /**
     * The set of punctuations marks.
     */
    private Punctuations punctuations;
    
    /**
     * Constructor.
     */
    public SimpleCorpus() {
        this(SimpleSentenceSplitter.getInstance(), new SimpleTokenizer(), EnglishStopWords.DEFAULT, EnglishPunctuations.getInstance());
    }

    /**
     * Constructor.
     *
     * @param splitter the sentence splitter.
     * @param tokenizer the word tokenizer.
     * @param stopWords the set of stop words to exclude.
     * @param punctuations the set of punctuation marks to exclude. Set to null to keep all punctuation marks.
     */
    public SimpleCorpus(SentenceSplitter splitter, Tokenizer tokenizer, StopWords stopWords, Punctuations punctuations) {
        this.splitter = splitter;
        this.tokenizer = tokenizer;
        this.stopWords = stopWords;
        this.punctuations = punctuations;
    }

    /**
     * Add a document to the corpus.
     */
    public Text add(String id, String title, String body) {
        ArrayList<String> bag = new ArrayList<>();
        
        for (String sentence : splitter.split(body)) {
            String[] tokens = tokenizer.split(sentence);
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = tokens[i].toLowerCase();
            }

            for (String w : tokens) {
                boolean keep = true;
                if (punctuations != null && punctuations.contains(w)) {
                    keep = false;
                } else if (stopWords != null && stopWords.contains(w)) {
                    keep = false;
                }

                if (keep) {
                    size++;
                    bag.add(w);
                    
                    Integer f = freq.get(w);
                    if (f == null) {
                        f = 1;
                    } else {
                        f = f + 1;
                    }

                    freq.put(w, f);
                }
            }

            for (int i = 0; i < tokens.length - 1; i++) {
                String w1 = tokens[i];
                String w2 = tokens[i + 1];
                
                if (freq.containsKey(w1) && freq.containsKey(w2)) {
                    Bigram bigram = new Bigram(w1, w2);
                    Integer f = freq2.get(bigram);
                    if (f == null) {
                        f = 1;
                    } else {
                        f = f + 1;
                    }

                    freq2.put(bigram, f);
                }
            }
        }

        String[] words = new String[bag.size()];
        for (int i = 0; i < words.length; i++) {
            words[i] = bag.get(i);
        }

        SimpleText doc = new SimpleText(id, title, body, words);
        docs.add(doc);

        for (String term : doc.unique()) {
            List<SimpleText> hit = invertedFile.get(term);
            if (hit == null) {
                hit = new ArrayList<>();
                invertedFile.put(term, hit);
            }
            hit.add(doc);
        }

        return doc;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public int getNumDocuments() {
        return docs.size();
    }

    @Override
    public int getNumTerms() {
        return freq.size();
    }

    @Override
    public long getNumBigrams() {
        return freq2.size();
    }

    @Override
    public int getAverageDocumentSize() {
        return (int) (size / docs.size());
    }

    @Override
    public int getTermFrequency(String term) {
        Integer f = freq.get(term);

        if (f == null) {
            return 0;
        }

        return f;
    }

    @Override
    public int getBigramFrequency(Bigram bigram) {
        Integer f = freq2.get(bigram);

        if (f == null) {
            return 0;
        }

        return f;
    }

    @Override
    public Iterator<String> getTerms() {
        return freq.keySet().iterator();
    }

    @Override
    public Iterator<Bigram> getBigrams() {
        return freq2.keySet().iterator();
    }

    @Override
    public Iterator<Text> search(String term) {
        if (invertedFile.containsKey(term)) {
            ArrayList<Text> hits = new ArrayList<>(invertedFile.get(term));
            return hits.iterator();
        } else {
            return Collections.emptyIterator();
        }
    }

    @Override
    public Iterator<Relevance> search(RelevanceRanker ranker, String term) {
        if (invertedFile.containsKey(term)) {
            List<SimpleText> hits = invertedFile.get(term);

            int n = hits.size();

            ArrayList<Relevance> rank = new ArrayList<>(n);
            for (SimpleText doc : hits) {
                int tf = doc.tf(term);
                rank.add(new Relevance(doc, ranker.rank(this, doc, term, tf, n)));
            }

            Collections.sort(rank, Collections.reverseOrder());
            return rank.iterator();
        } else {
            return Collections.emptyIterator();
        }
    }

    @Override
    public Iterator<Relevance> search(RelevanceRanker ranker, String[] terms) {
        Set<SimpleText> hits = new HashSet<>();

        for (int i = 0; i < terms.length; i++) {
            if (invertedFile.containsKey(terms[i])) {
                hits.addAll(invertedFile.get(terms[i]));
            }
        }

        int n = hits.size();
        if (n == 0) {
            return Collections.emptyIterator();
        }
        
        ArrayList<Relevance> rank = new ArrayList<>(n);
        for (SimpleText doc : hits) {
            double r = 0.0;
            for (int i = 0; i < terms.length; i++) {
                int tf = doc.tf(terms[i]);
                r += ranker.rank(this, doc, terms[i], tf, n);
            }

            rank.add(new Relevance(doc, r));
        }

        Collections.sort(rank, Collections.reverseOrder());
        return rank.iterator();
    }
}
