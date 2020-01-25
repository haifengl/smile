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

package smile.data;

import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 *
 * @author Haifeng Li
 */
public interface Sequence {

    class Dataset {
        /** The dimension of data. */
        public final int p;
        /** The number of classes. */
        public final int k;
        public final int[][] x;
        public final int[] y;
        public final Tuple[][] seq;
        public final int[][] label;

        public Dataset(int p, int k, int[][] x, int[] y, Tuple[][] seq, int[][] label) {
            this.p = p;
            this.k = k;
            this.x = x;
            this.y = y;
            this.seq = seq;
            this.label = label;
        }
    }

    static Dataset read(String resource) throws IOException {
        int p = 0;
        int k = 0;
        ArrayList<int[][]> x = new ArrayList<>();
        ArrayList<int[]> y = new ArrayList<>();

        ArrayList<int[]> seq = new ArrayList<>();
        ArrayList<Integer> label = new ArrayList<>();

        int id = 1;
        try (BufferedReader input = smile.util.Paths.getTestDataReader(resource)) {
            String[] words = input.readLine().split(" ");
            int nseq = Integer.parseInt(words[0]);
            k = Integer.parseInt(words[1]);
            p = Integer.parseInt(words[2]);

            String line = null;
            while ((line = input.readLine()) != null) {
                words = line.split(" ");
                int seqid = Integer.parseInt(words[0]);
                int pos = Integer.parseInt(words[1]);
                int len = Integer.parseInt(words[2]);

                int[] feature = new int[len];
                for (int i = 0; i < len; i++) {
                    feature[i] = Integer.parseInt(words[i+3]);
                }

                if (seqid == id) {
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                } else {
                    id = seqid;

                    x.add(seq.toArray(new int[seq.size()][]));
                    y.add(label.stream().mapToInt(yi -> yi).toArray());

                    seq = new ArrayList<>();
                    label = new ArrayList<>();
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                }
            }

            x.add(seq.toArray(new int[seq.size()][]));
            y.add(label.stream().mapToInt(yi -> yi).toArray());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        int[] base = new int[x.get(0)[0].length];
        StructType schema = new StructType(IntStream.range(0, x.get(0)[0].length).mapToObj(i -> {
            int[] values = x.stream().flatMapToInt(xi -> Arrays.stream(xi).mapToInt(xij -> xij[i])).distinct().toArray();
            Arrays.sort(values);
            base[i] = values[0];
            NominalScale scale = new NominalScale(IntStream.range(0, values.length).mapToObj(String::valueOf).toArray(String[]::new));
            return new StructField("V"+(i+1), DataTypes.IntegerType, scale);
        }).toArray(StructField[]::new));

        int n = x.stream().mapToInt(xi -> xi.length).sum();
        Dataset dataset = new Dataset(p, k, new int[n][], new int[n], new Tuple[x.size()][], y.toArray(new int[y.size()][]));
        for (int i = 0, l = 0; i < x.size(); i++) {
            int[][] xi = x.get(i);
            int[] yi = y.get(i);
            Tuple[] seqi = new Tuple[xi.length];
            dataset.seq[i] = seqi;
            for (int j = 0; j < xi.length; j++) {
                seqi[j] = tuple(schema, xi[j], base);
                dataset.x[l] = xi[j];
                dataset.y[l++] = yi[j];
            }
        }

        return dataset;
    }

    static Tuple tuple(StructType schema, int[] x, int[] base) {
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
                return smile.util.Strings.toString(data);
            }
        };
    }
}
