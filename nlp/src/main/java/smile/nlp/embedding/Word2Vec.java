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
package smile.nlp.embedding;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.DataFrame;
import smile.data.vector.FloatVector;

/**
 * Word2vec is a group of related models that are used to produce word
 * embeddings. These models are shallow, two-layer neural networks that
 * are trained to reconstruct linguistic contexts of words. Word2vec
 * takes as its input a large corpus of text and produces a vector space,
 * typically of several hundred dimensions, with each unique word in the
 * corpus being assigned a corresponding vector in the space. Word vectors
 * are positioned in the vector space such that words that share common
 * contexts in the corpus are located close to one another in the space.
 * <p>
 * Word2vec can utilize either of two model architectures to produce
 * a distributed representation of words: continuous bag-of-words (CBOW)
 * or continuous skip-gram. In the continuous bag-of-words architecture,
 * the model predicts the current word from a window of surrounding context
 * words. The order of context words does not influence prediction
 * (bag-of-words assumption). In the continuous skip-gram architecture,
 * the model uses the current word to predict the surrounding window of
 * context words. The skip-gram architecture weighs nearby context words
 * more heavily than more distant context words. According to the authors'
 * note, CBOW is faster while skip-gram is slower but does a better job
 * for infrequent words.
 * <p>
 * <a href="https://nlp.stanford.edu/projects/glove/">GloVe</a>
 * (Global Vectors for Word Representation) is another popular
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
public class Word2Vec {
    /** The vocabulary. */
    public final String[] words;
    /** The vector space. */
    public final DataFrame vectors;
    /** The word-to-index map. */
    private final HashMap<String, Integer> map;

    /**
     * Constructor.
     * @param words the vocabulary.
     * @param vectors the vectors of d x n, where d is the dimension
     *                and n is the size of vocabulary.
     */
    public Word2Vec(String[] words, float[][] vectors) {
        this.words = words;
        this.vectors = new DataFrame(IntStream.range(0, vectors.length)
                .mapToObj(i -> new FloatVector("V"+(i+1), vectors[i]))
                .toArray(FloatVector[]::new));

        int n = words.length;
        map = new HashMap<>(n * 4 / 3 + 3);
        for (int i = 0; i < n; i++) {
            map.put(words[i], i);
        }
    }

    /**
     * Returns the dimension of embedding vector space.
     * @return the dimension of embedding vector space.
     */
    public int dimension() {
        return vectors.ncol();
    }

    /**
     * Returns the embedding vector of a word.
     * @param word the word.
     * @return the embedding vector.
     */
    public float[] apply(String word) {
        Integer index = map.get(word);
        if (index == null) return null;

        int i = index;
        int dim = vectors.ncol();
        float[] vector = new float[dim];
        for (int j = 0; j < dim; j++) {
            vector[j] = vectors.getFloat(i, j);
        }

        return vector;
    }

    /**
     * Returns the embedding vector of a word, or empty if the word is not
     * in the vocabulary.
     * @param word the word.
     * @return the embedding vector, or empty if not found.
     */
    public java.util.Optional<float[]> lookup(String word) {
        return java.util.Optional.ofNullable(apply(word));
    }

    /**
     * Returns true if the word is in the vocabulary.
     * @param word the word.
     * @return true if the vocabulary contains the word.
     */
    public boolean contains(String word) {
        return map.containsKey(word);
    }

    /**
     * Returns the size of the vocabulary.
     * @return the number of words in the vocabulary.
     */
    public int size() {
        return words.length;
    }

    /**
     * Loads a <a href="https://code.google.com/archive/p/word2vec/">pre-trained</a>
     * word2vec model from binary file of ByteOrder.LITTLE_ENDIAN.
     * @param file the path to model file.
     * @throws IOException when fails to read the file.
     * @return the word2vec model.
     */
    public static Word2Vec of(Path file) throws IOException {
        return of(file, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Loads a <a href="https://code.google.com/archive/p/word2vec/">pre-trained</a>
     * word2vec model from binary file.
     * @param file the path to model file.
     * @param order the byte order of model file.
     * @throws IOException when fails to read the file.
     * @return the word2vec model.
     */
    public static Word2Vec of(Path file, ByteOrder order) throws IOException {
        final long GB = 1024 * 1024 * 1024;

        try (FileInputStream input = new FileInputStream(file.toFile())) {
            FileChannel channel = input.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0,
                    Math.min(channel.size(), Integer.MAX_VALUE));
            buffer.order(order);

            // Java memory-mapping is up to 2GB. Map chunk per 1GB.
            int blocks = 1;

            StringBuilder sb = new StringBuilder();
            char c = (char) buffer.get();
            while (c != '\n') {
                sb.append(c);
                c = (char) buffer.get();
            }

            String line = sb.toString();
            String[] tokens = line.split("\\s+");
            if (tokens.length != 2) {
                throw new IllegalStateException("Invalid first line: " + line);
            }

            int size = Integer.parseInt(tokens[0]);
            int dim = Integer.parseInt(tokens[1]);

            String[] words = new String[size];
            float[][] vectors = new float[dim][size];

            for (int i = 0; i < size; i++) {
                // read vocab
                sb.setLength(0);
                c = (char) buffer.get();
                while (c != ' ') {
                    // some binary files have newline
                    if (c != '\n') sb.append(c);
                    c = (char) buffer.get();
                }

                // some binary files have newline
                words[i] = sb.toString();

                // read vector
                FloatBuffer floatBuffer = buffer.asFloatBuffer();
                for (int j = 0; j < dim; j++) {
                    vectors[j][i] = floatBuffer.get();
                }
                buffer.position(buffer.position() + 4 * dim);

                // remap file
                if (buffer.position() > GB) {
                    int newPosition = (int) (buffer.position() - GB);
                    long chunk = Math.min(channel.size() - GB * blocks, Integer.MAX_VALUE);
                    buffer = channel.map(FileChannel.MapMode.READ_ONLY, GB * blocks, chunk);
                    buffer.order(order);
                    buffer.position(newPosition);
                    blocks += 1;
                }
            }

            return new Word2Vec(words, vectors);
        }
    }

    /**
     * Loads a GloVe model from a text file.
     * Each line must have the form: {@code word f1 f2 ... fd}.
     * where {@code d} is the embedding dimension.
     * All lines must have the same number of dimensions.
     *
     * @param file the path to model file.
     * @throws IOException when fails to read the file.
     * @throws IllegalArgumentException if the file is empty or lines have
     *         inconsistent dimensions.
     * @return the word embedding model.
     */
    public static Word2Vec glove(Path file) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            List<String> words = new ArrayList<>(1000000);
            List<float[]> vectors = new ArrayList<>(1000000);
            stream.forEach(line -> {
                String[] tokens = line.split("\\s+");
                if (tokens.length < 2) {
                    throw new IllegalArgumentException(
                            "Invalid GloVe line (expected 'word f1 f2 ...'): " + line);
                }
                words.add(tokens[0]);
                float[] vector = new float[tokens.length - 1];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = Float.parseFloat(tokens[i + 1]);
                }
                vectors.add(vector);
            });

            if (vectors.isEmpty()) {
                throw new IllegalArgumentException("GloVe file is empty: " + file);
            }

            int n = vectors.size();
            int d = vectors.getFirst().length;

            // Validate that all vectors have the same dimension
            for (int i = 1; i < n; i++) {
                if (vectors.get(i).length != d) {
                    throw new IllegalArgumentException(
                            "Inconsistent vector dimension at line " + (i + 1)
                                    + ": expected " + d + " but got " + vectors.get(i).length);
                }
            }

            float[][] pivot = new float[d][n];
            for (int i = 0; i < n; i++) {
                float[] vector = vectors.get(i);
                for (int j = 0; j < d; j++) {
                    pivot[j][i] = vector[j];
                }
            }

            return new Word2Vec(words.toArray(new String[0]), pivot);
        }
    }
}