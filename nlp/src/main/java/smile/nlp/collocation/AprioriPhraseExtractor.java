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

package smile.nlp.collocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import smile.nlp.NGram;
import smile.nlp.dictionary.EnglishPunctuations;
import smile.nlp.dictionary.EnglishStopWords;

/**
 * An Apiori-like algorithm to extract n-gram phrases. The algorithm was
 * proposed in "A Study Using n-gram Features for Text Categorization" by
 * Johannes Furnkranz.
 * <p>
 * The algorithm takes a collection of sentences and generates all n-grams of
 * length at most MaxNGramSize that occur at least MinFrequency times in the
 * sentences.
 * 
 * @author Haifeng Li
 */
public class AprioriPhraseExtractor {

    /** Extracts n-gram phrases.
     * 
     * @param sentences A collection of sentences (already split).
     * @param maxNGramSize The maximum length of n-gram
     * @param minFrequency The minimum frequency of n-gram in the sentences.
     * @return An array list of sets of n-grams. The i-th entry is the set of i-grams.
     */
    public ArrayList<ArrayList<NGram>> extract(Collection<String[]> sentences, int maxNGramSize, int minFrequency) {
        ArrayList<Set<NGram>> features = new ArrayList<>(maxNGramSize + 1);
        features.add(new HashSet<>());
        for (int n = 1; n <= maxNGramSize; n++) {
            Map<NGram, Integer> candidates = new HashMap<>();
            Set<NGram> feature = new HashSet<>();
            features.add(feature);
            Set<NGram> feature_1 = features.get(n - 1);
            for (String[] sentence : sentences) {
                for (int i = 0; i <= sentence.length - n; i++) {
                    NGram ngram  = new NGram(Arrays.copyOfRange(sentence, i, i+n));
                    boolean add = false;
                    if (n == 1) {
                        add = true;
                    } else {
                        NGram initialGram  = new NGram(Arrays.copyOfRange(sentence, i, i+n-1));
                        NGram finalGram  = new NGram(Arrays.copyOfRange(sentence, i+1, i+n));
                        if (feature_1.contains(initialGram) && feature_1.contains(finalGram)) {
                            add = true;
                        }
                    }
                    
                    if (add) {
                        if (candidates.containsKey(ngram)) {
                            candidates.put(ngram, candidates.get(ngram) + 1);
                        } else {
                            candidates.put(ngram, 1);
                        }
                    }
                }
            }
            
            for (Map.Entry<NGram, Integer> entry : candidates.entrySet()) {
                if (entry.getValue() >= minFrequency) {
                    NGram ngram = entry.getKey();
                    if (ngram.words.length == 1 && EnglishPunctuations.getInstance().contains(ngram.words[0])) {
                        continue;
                    }
                    
                    ngram.freq = entry.getValue();
                    feature.add(ngram);
                }
            }
        }
        
        // filter out stop words
        ArrayList<ArrayList<NGram>> results = new ArrayList<>();
        for (Set<NGram> ngrams : features) {
            ArrayList<NGram> result = new ArrayList<>();
            results.add(result);
            for (NGram ngram : ngrams) {
                boolean stopWord = true;
                if (!EnglishStopWords.DEFAULT.contains(ngram.words[0]) && !EnglishStopWords.DEFAULT.contains(ngram.words[ngram.words.length-1])) {
                    for (String word : ngram.words) {
                        if (!EnglishStopWords.DEFAULT.contains(word)) {
                            stopWord = false;
                            break;
                        }
                    }
                }

                if (!stopWord) {
                    result.add(ngram);
                }
            }
            
            Collections.sort(result);
            Collections.reverse(result);
        }
        
        return results;
    }
}
