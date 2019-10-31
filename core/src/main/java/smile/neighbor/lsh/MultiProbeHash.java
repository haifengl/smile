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

package smile.neighbor.lsh;

import java.util.Arrays;

/**
 * The hash function for data in Euclidean spaces.
 */
public class MultiProbeHash extends Hash {
    private static final long serialVersionUID = 2L;

    /**
     * The minimum values of hashing functions for given dataset.
     */
    double[] umin;
    /**
     * The maximum values of hashing functions for given dataset.
     */
    double[] umax;


    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param k the number of random projection hash functions, which is usually
     *          set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently away
     *          from 0. But we should not choose an w value that is too large,
     *          which will increase the query time.
     * @param H the size of universal hash tables.
     */
    public MultiProbeHash(int d, int k, double w, int H) {
        super(d, k, w, H);

        umin = new double[k];
        umax = new double[k];

        Arrays.fill(umin, Double.POSITIVE_INFINITY);
        Arrays.fill(umax, Double.NEGATIVE_INFINITY);
    }

    /**
     * This should only be used for adding data.
     * @param x the vector to be hashed.
     * @return the bucket of hash table for given vector x.
     */
    private int mphash(double[] x) {
        double[] h = new double[k];
        a.ax(x, h);

        long g = 0;
        for (int i = 0; i < k; i++) {
            double hi = (h[i] + b[i]) / w;

            if (hi < umin[i]) {
                umin[i] = hi;
            }

            if (hi > umax[i]) {
                umax[i] = hi;
            }

            g += c[i] * (int) Math.floor(hi);
        }

        int gint = (int) (g % P);
        return gint >= 0 ? gint : gint + P;
    }

    @Override
    public void add(int index, double[] x) {
        int bucket = mphash(x);
        int i = bucket % H;

        if (table[i] == null) {
            table[i] = new Bucket(bucket);
        }

        table[i].add(index);
    }
}
