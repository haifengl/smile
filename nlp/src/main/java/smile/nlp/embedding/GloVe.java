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

package smile.nlp.embedding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Global Vectors for Word Representation.
 * <a href="https://nlp.stanford.edu/projects/glove/">GloVe</a> is an
 * unsupervised learning algorithm for obtaining vector representations
 * for words.
 * <p>
 * GloVe is essentially a log-bilinear model with a weighted least-squares
 * objective. The main intuition underlying the model is the simple
 * observation that ratios of word-word co-occurrence probabilities
 * have the potential for encoding some form of meaning.
 * <p>
 * Training is performed on aggregated global word-word co-occurrence
 * statistics from a corpus. The training objective of GloVe is to learn
 * word vectors such that their dot product equals the logarithm of the
 * words' probability of co-occurrence. Owing to the fact that the logarithm
 * of a ratio equals the difference of logarithms, this objective associates
 * (the logarithm of) ratios of co-occurrence probabilities with vector
 * differences in the word vector space. Because these ratios can encode
 * some form of meaning, this information gets encoded as vector differences
 * as well.
 *
 * @author Haifeng Li
 */
public class GloVe {
    /**
     * Loads a GloVe model.
     * @param file the path to model file.
     * @throws IOException when fails to read the file.
     * @return the GloVe model.
     */
    public static Word2Vec of(Path file) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            List<String> words = new ArrayList<>(1000000);
            List<float[]> vectors = new ArrayList<>(1000000);
            stream.forEach(line -> {
                String[] tokens = line.split("\\s+");
                words.add(tokens[0]);
                float[] vector = new float[tokens.length-1];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = Float.parseFloat(tokens[i+1]);
                }
                vectors.add(vector);
            });

            int n = vectors.size();
            int d = vectors.get(0).length;
            float[][] pivot = new float[d][n];
            for (int i = 0; i < n; i++) {
                float[] vector = vectors.get(i);
                for (int j = 0; j < d; j++) {
                    pivot[j][i] = vector[j];
                }
            }

            return new Word2Vec(words.toArray(new String[n]), pivot);
        }
    }
}