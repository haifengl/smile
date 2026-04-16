/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp;

import java.util.*;
import smile.nlp.dictionary.EnglishPunctuations;
import smile.nlp.dictionary.EnglishStopWords;
import smile.util.MutableInt;

/**
 * An n-gram is a contiguous sequence of n words from a given sequence of text.
 * An n-gram of size 1 is referred to as a unigram; size 2 is a bigram;
 * size 3 is a trigram.
 *
 * @param words the word sequence.
 * @param count the total number of occurrences of n-gram in the corpus.
 * @author Haifeng Li
 */
public record NGram(String[] words, int count) implements Comparable<NGram> {

    /**
     * Constructor.
     * @param words the n-gram word sequence.
     */
    public NGram(String[] words) {
        this(words, -1);
    }

    @Override
    public String toString() {
        String s = '[' + String.join(", ", words) + ']';
        return count > 0 ? String.format("(%s, %d)", s, count) : s;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof NGram other)) {
            return false;
        }

        return Arrays.equals(words, other.words);
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
    public static NGram[][] apiori(Collection<String[]> sentences, int maxNGramSize, int minFrequency) {
        ArrayList<Set<NGram>> features = new ArrayList<>(maxNGramSize + 1);
        Set<NGram> feature = new HashSet<>();
        features.add(feature);

        EnglishPunctuations punctuations = EnglishPunctuations.getInstance();
        for (int n = 1; n <= maxNGramSize; n++) {
            Map<NGram, MutableInt> candidates = new HashMap<>();

            for (String[] sentence : sentences) {
                for (int i = 0; i <= sentence.length - n; i++) {
                    var ngram = new NGram(Arrays.copyOfRange(sentence, i, i+n));

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
            for (Map.Entry<NGram, MutableInt> entry : candidates.entrySet()) {
                MutableInt count = entry.getValue();
                if (count.value >= minFrequency) {
                    NGram ngram = entry.getKey();
                    if (ngram.words.length == 1 && punctuations.contains(ngram.words[0])) {
                        continue;
                    }

                    feature.add(new NGram(ngram.words, count.value));
                }
            }
        }

        // Filter out n-grams whose first or last word is a stop word.
        // (An n-gram is kept only when neither boundary word is a stop word,
        // which also ensures at least one content word is present.)
        EnglishStopWords stopWords = EnglishStopWords.DEFAULT;
        return features.stream().map(ngrams -> {
            NGram[] collocations = ngrams.stream()
                    .filter(ngram -> {
                        String[] words = ngram.words;
                        return !stopWords.contains(words[0])
                                && !stopWords.contains(words[words.length - 1]);
                    })
                    .toArray(NGram[]::new);

            Arrays.sort(collocations, Collections.reverseOrder());
            return collocations;
        }).toArray(NGram[][]::new);
    }
}

