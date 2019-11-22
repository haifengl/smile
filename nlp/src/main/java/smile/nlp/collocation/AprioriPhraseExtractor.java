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
import smile.util.MutableInt;

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
public interface AprioriPhraseExtractor {

    /** Extracts n-gram phrases.
     * 
     * @param sentences A collection of sentences (already split).
     * @param maxNGramSize The maximum length of n-gram
     * @param minFrequency The minimum frequency of n-gram in the sentences.
     * @return An array list of sets of n-grams. The i-th entry is the set of i-grams.
     */
    static ArrayList<ArrayList<NGram>> extract(Collection<String[]> sentences, int maxNGramSize, int minFrequency) {
        ArrayList<Set<NGram>> features = new ArrayList<>(maxNGramSize + 1);
        features.add(new HashSet<>());
        for (int n = 1; n <= maxNGramSize; n++) {
            Map<NGram, MutableInt> candidates = new HashMap<>();
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
                        MutableInt count = candidates.get(ngram);
                        if (count == null) {
                            candidates.put(ngram, new MutableInt(1));
                        } else {
                            count.increment();
                        }
                    }
                }
            }
            
            for (Map.Entry<NGram, MutableInt> entry : candidates.entrySet()) {
                MutableInt count = entry.getValue();
                if (count.value >= minFrequency) {
                    NGram ngram = entry.getKey();
                    if (ngram.words.length == 1 && EnglishPunctuations.getInstance().contains(ngram.words[0])) {
                        continue;
                    }
                    
                    feature.add(new NGram(ngram.words, count.value));
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
