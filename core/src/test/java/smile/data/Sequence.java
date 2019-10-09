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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author Haifeng
 */
public interface Sequence {

    class Dataset {
        /** The dimension of data. */
        public int p;
        /** The number of classes. */
        public int k;
        public int[][] x;
        public int[] y;

        public Dataset(int p, int k, int[][] x, int[] y) {
            this.p = p;
            this.k = k;
            this.x = x;
            this.y = y;
        }
    }

    static Dataset read(String resource) throws IOException {
        try (BufferedReader input = smile.util.Paths.getTestDataReader(resource)) {
            String[] words = input.readLine().split(" ");
            int nseq = Integer.parseInt(words[0]);
            int k = Integer.parseInt(words[1]);
            int p = Integer.parseInt(words[2]);

            ArrayList<int[]> x = new ArrayList<>();
            ArrayList<Integer> y = new ArrayList<>();
            input.lines().forEach(line -> {
                String[] tokens = line.split(" ");
                int seqid = Integer.parseInt(tokens[0]);
                int pos = Integer.parseInt(tokens[1]);
                int len = Integer.parseInt(tokens[2]);

                int[] feature = new int[len];
                for (int j = 0; j < len; j++) {
                    feature[j] = Integer.parseInt(tokens[j+3]);
                }

                x.add(feature);
                y.add(Integer.valueOf(tokens[len+3]));
            });

            return new Dataset(p, k, x.toArray(new int[x.size()][]), y.stream().mapToInt(yi -> yi).toArray());
        }
    }
}
