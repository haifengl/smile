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

import java.util.*;
import smile.nlp.dictionary.EnglishPunctuations;
import smile.nlp.dictionary.EnglishStopWords;
import smile.util.MutableInt;

/**
 * An n-gram is a contiguous sequence of n words from a given sequence of text.
 * An n-gram of size 1 is referred to as a unigram; size 2 is a bigram;
 * size 3 is a trigram.
 *
 * @author Haifeng Li
 */
public class NGram extends smile.nlp.NGram implements Comparable<NGram> {

    /**
     * The frequency of n-gram in the corpus.
     */
    public final int count;

    /**
     * Constructor.
     * @param words the n-gram word sequence.
     * @param count the frequency of n-gram in the corpus.
     */
    public NGram(String[] words, int count) {
        super(words);
        this.count = count;
    }

    @Override
    public String toString() {
        return String.format("(%s, %d)", super.toString(), count);
    }

    @Override
    public int compareTo(NGram o) {
        return Integer.compare(count, o.count);
    }

    /**
     * Extracts n-gram phrases by an Apiori-like algorithm. The algorithm was
     * proposed in "A Study Using n-gram Features for Text Categorization" by
     * Johannes Furnkranz.
     * <p>
     * The algorithm takes a collection of sentences and generates all n-grams of
     * length at most MaxNGramSize that occur at least MinFrequency times in the
     * sentences.
     *
     * @param sentences A collection of sentences (already split).
     * @param maxNGramSize The maximum length of n-gram
     * @param minFrequency The minimum frequency of n-gram in the sentences.
     * @return An array of n-gram sets. The i-th entry is the set of i-grams.
     */
    public static NGram[][] of(Collection<String[]> sentences, int maxNGramSize, int minFrequency) {
        ArrayList<Set<NGram>> features = new ArrayList<>(maxNGramSize + 1);
        Set<NGram> feature = new HashSet<>();
        features.add(feature);

        EnglishPunctuations punctuations = EnglishPunctuations.getInstance();
        for (int n = 1; n <= maxNGramSize; n++) {
            Map<smile.nlp.NGram, MutableInt> candidates = new HashMap<>();

            for (String[] sentence : sentences) {
                for (int i = 0; i <= sentence.length - n; i++) {
                    smile.nlp.NGram ngram = new smile.nlp.NGram(Arrays.copyOfRange(sentence, i, i+n));

                    boolean add = false;
                    if (n == 1) {
                        add = true;
                    } else {
                        NGram initialGram  = new NGram(Arrays.copyOfRange(sentence, i, i+n-1), 0);
                        NGram finalGram  = new NGram(Arrays.copyOfRange(sentence, i+1, i+n), 0);
                        if (feature.contains(initialGram) && feature.contains(finalGram)) {
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

            feature = new HashSet<>();
            features.add(feature);
            for (Map.Entry<smile.nlp.NGram, MutableInt> entry : candidates.entrySet()) {
                MutableInt count = entry.getValue();
                if (count.value >= minFrequency) {
                    smile.nlp.NGram ngram = entry.getKey();
                    if (ngram.words.length == 1 && punctuations.contains(ngram.words[0])) {
                        continue;
                    }

                    feature.add(new NGram(ngram.words, count.value));
                }
            }
        }

        // filter out stop words
        EnglishStopWords stopWords = EnglishStopWords.DEFAULT;
        return features.stream().map(ngrams -> {
            NGram[] collocations = ngrams.stream().filter(ngram -> {
                boolean stopWord = true;
                String[] words = ngram.words;
                if (!stopWords.contains(words[0]) && !stopWords.contains(words[words.length - 1])) {
                    for (String word : words) {
                        if (!stopWords.contains(word)) {
                            stopWord = false;
                            break;
                        }
                    }
                }
                return !stopWord;
            }).toArray(NGram[]::new);

            Arrays.sort(collocations, Collections.reverseOrder());
            return collocations;
        }).toArray(NGram[][]::new);
    }
}
