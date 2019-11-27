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
 *
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
 *
 * @author Haifeng Li
 */
public class Word2Vec {
    /** The vocabulary. */
    public final String[] words;
    /** The vector space. */
    public final DataFrame vectors;
    /** The word-to-index map. */
    private HashMap<String, Integer> map;

    /**
     * Constructor.
     * @param words the vocabulary.
     * @param vectors the vectors of d x n, where d is the dimension
     *                and n is the size of vocabulary.
     */
    public Word2Vec(String[] words, float[][] vectors) {
        this.words = words;
        this.vectors = DataFrame.of(
                IntStream.range(0, vectors.length)
                        .mapToObj(i -> FloatVector.of("V"+(i+1), vectors[i]))
                        .toArray(FloatVector[]::new)
        );

        int n = words.length;
        map = new HashMap<>(n * 4 / 3 + 3);
        for (int i = 0; i < n; i++) {
            map.put(words[i], i);
        }
    }

    /** Returns the dimension of vector space. */
    public int dimension() {
        return vectors.ncols();
    }

    /** Returns the vector embedding of a word. */
    public float[] get(String word) {
        Integer index = map.get(word);
        if (index == null) return null;

        int i = index;
        int dim = vectors.ncols();
        float[] vector = new float[dim];
        for (int j = 0; j < dim; j++) {
            vector[j] = vectors.getFloat(i, j);
        }

        return vector;
    }

    /** Returns the vector embedding of a word. For Scala convenience. */
    public float[] apply(String word) {
        return get(word);
    }

    /**
     * Loads a word2vec model from binary file of ByteOrder.LITTLE_ENDIAN.
     */
    public static Word2Vec of(Path file) throws IOException {
        return of(file, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Loads a word2vec model from binary file.
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
     * Loads a GloVe model from text file.
     */
    public static Word2Vec text(Path file) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            List<String> words = new ArrayList<>(1000000);
            List<float[]> vectors = new ArrayList<>(1000000);
            stream.forEach(line -> {
                String[] tokens = line.split("\\s+");
                words.add(tokens[0]);
                float[] vector = new float[tokens.length-1];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = Float.valueOf(tokens[i+1]);
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