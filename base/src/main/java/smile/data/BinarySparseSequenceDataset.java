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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Binary sparse sequence dataset. Each sequence element has sparse binary feature values.
 *
 * @author Haifeng Li
 */
public class BinarySparseSequenceDataset extends SimpleDataset<int[][], int[]> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BinarySparseSequenceDataset.class);
    /** The number of features of sequence elements. */
    private final int p;
    /** The number of classes of sequence elements. */
    private final int k;
    /** The sequence data as tuples. */
    private final Tuple[][] seq;
    /** The sequence data as tuples. */
    private final int[][] tag;

    /**
     * Constructor.
     * @param p The number of features of sequence elements.
     * @param k The number of classes of sequence elements.
     * @param data The sample instances.
     */
    public BinarySparseSequenceDataset(int p, int k, List<SampleInstance<int[][], int[]>> data) {
        super(data);
        this.p = p;
        this.k = k;

        int length = data.getFirst().x()[0].length;
        int[] base = new int[length];
        StructType schema = new StructType(IntStream.range(0, length).mapToObj(i -> {
            int[] values = data.stream()
                    .flatMapToInt(instance -> Arrays.stream(instance.x()).mapToInt(xj -> xj[i]))
                    .distinct().toArray();
            Arrays.sort(values);
            base[i] = values[0];
            NominalScale scale = new NominalScale(IntStream.range(0, values.length).mapToObj(String::valueOf).toArray(String[]::new));
            return new StructField("V"+(i+1), DataTypes.IntType, scale);
        }).toArray(StructField[]::new));

        int n = data.size();
        seq = new Tuple[n][];
        tag = new int[n][];
        for (int i = 0; i < n; i++) {
            int[][] xi = data.get(i).x();
            tag[i] = data.get(i).y();
            seq[i] = new Tuple[xi.length];

            for (int j = 0; j < xi.length; j++) {
                seq[i][j] = tuple(schema, xi[j], base);
            }
        }
    }

    /**
     * Returns the number of features of sequence elements.
     * @return the number of features of sequence elements.
     */
    public int p() {
        return p;
    }

    /**
     * Returns the number of classes of sequence elements.
     * @return the number of classes of sequence elements.
     */
    public int k() {
        return k;
    }

    /**
     * Returns the sequence data.
     * @return the sequence data.
     */
    public Tuple[][] seq() {
        return seq;
    }

    /**
     * Returns the sequence element label.
     * @return the sequence element label.
     */
    public int[][] tag() {
        return tag;
    }

    /**
     * Returns the flatten sequence data.
     * @return the flatten sequence data.
     */
    public int[][] x() {
        return instances.stream().map(SampleInstance::x)
                .flatMap(Arrays::stream).toArray(int[][]::new);
    }

    /**
     * Returns the flatten sequence element label.
     * @return the flatten sequence element label.
     */
    public int[] y() {
        return instances.stream().map(SampleInstance::y)
                .flatMapToInt(Arrays::stream).toArray();
    }

    /**
     * Loads a sparse sequence dataset.
     * @param path the path to data file.
     * @return the sparse sequence dataset.
     * @throws IOException when fails to read the file.
     */
    public static BinarySparseSequenceDataset load(Path path) throws IOException {
        try (BufferedReader input = Files.newBufferedReader(path)) {
            ArrayList<SampleInstance<int[][], int[]>> instances = new ArrayList<>();
            ArrayList<int[]> seq = new ArrayList<>();
            ArrayList<Integer> tag = new ArrayList<>();

            int currentSeqID = 0;
            String[] words = input.readLine().split(" ");
            int size = Integer.parseInt(words[0]);
            int k = Integer.parseInt(words[1]);
            int p = Integer.parseInt(words[2]);
            logger.info("Loading {} sequences, {} classes, {} features", size, k, p);

            String line;
            while ((line = input.readLine()) != null) {
                words = line.split(" ");
                int seqID = Integer.parseInt(words[0]);
                int pos = Integer.parseInt(words[1]);
                int len = Integer.parseInt(words[2]);

                int[] feature = new int[len];
                for (int i = 0; i < len; i++) {
                    feature[i] = Integer.parseInt(words[i+3]);
                }

                if (seqID != currentSeqID) {
                    currentSeqID = seqID;
                    if (!seq.isEmpty()) {
                        int[][] x = seq.toArray(new int[seq.size()][]);
                        int[] y = tag.stream().mapToInt(yi -> yi).toArray();
                        SampleInstance<int[][], int[]> instance = new SampleInstance<>(x, y);
                        instances.add(instance);

                        seq.clear();
                        tag.clear();
                    }
                }
                seq.add(feature);
                tag.add(Integer.valueOf(words[len + 3]));
            }

            int[][] x = seq.toArray(new int[seq.size()][]);
            int[] y = tag.stream().mapToInt(yi -> yi).toArray();
            SampleInstance<int[][], int[]> instance = new SampleInstance<>(x, y);
            instances.add(instance);
            return new BinarySparseSequenceDataset(p, k, instances);
        }
    }

    private static Tuple tuple(StructType schema, int[] x, int[] base) {
        int n = x.length;
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = x[i] - base[i];
        }

        return new Tuple() {
            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public Object get(int j) {
                return data[j];
            }

            @Override
            public int getInt(int j) {
                return data[j];
            }

            @Override
            public String toString() {
                return Arrays.toString(data);
            }
        };
    }
}
