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

import java.io.Serializable;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;

/**
 * The hash function for Euclidean spaces.
 *
 * @author Haifeng Li
 */
public class Hash implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The prime number in universal bucket hashing.
     */
    private final int P = 2147483647;
    /**
     * The range of universal hashing random integers [0, 2^29).
     */
    private final int MAX_HASH_RND = 536870912;
    /**
     * The size of hash table.
     */
    private int H;
    /**
     * The dimensionality of data.
     */
    private int d;
    /**
     * The number of random projections per hash value.
     */
    private int k;
    /**
     * The width of projection. The hash function is defined as floor((a * x + b) / w). The value
     * of w determines the bucket interval.
     */
    private double w;
    /**
     * The random integer used for universal bucket hashing.
     */
    private int[] r1;
    /**
     * The random integer used for universal hashing for control values.
     */
    private int[] r2;

    /**
     * The random vectors with entries chosen independently from a Gaussian
     * distribution.
     */
    private double[][] a;
    /**
     * Real numbers chosen uniformly from the range [0, w].
     */
    private double[] b;
    /**
     * Hash table.
     */
    private HashEntry[] table;

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param k the number of random projection hash functions, which is usually
     *          set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently away
     *          from 0. But we should not choose an w value that is too large,
     *          which will increase the query time.
     * @param H the size of universal hash tables.
     * @param r1 the random integer used for universal bucket hashing.
     * @param r2 the random integer used for universal hashing for control values.
     */
    public Hash(int d, int k, double w, int H, int[] r1, int[] r2) {
        if (d < 2) {
            throw new IllegalArgumentException("Invalid input space dimension: " + d);
        }

        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of random projections per hash value: " + k);
        }

        if (w <= 0.0) {
            throw new IllegalArgumentException("Invalid width of random projections: " + w);
        }

        if (H < 1) {
            throw new IllegalArgumentException("Invalid size of hash tables: " + H);
        }

        this.d = d;
        this.k = k;
        this.w = w;
        this.H = H;
        this.r1 = r1;
        this.r2 = r2;

        a = new double[k][d];
        b = new double[k];

        GaussianDistribution gaussian = GaussianDistribution.getInstance();
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < d; j++) {
                a[i][j] = gaussian.rand();
            }

            b[i] = MathEx.random(0, w);
        }

        table = new HashEntry[H];
    }

    /**
     * Returns the hash value of given vector x.
     * @param x the vector to be hashed.
     * @param m the m-<i>th</i> hash function to be employed.
     * @return the hash value.
     */
    private int hash(double[] x, int m) {
        double g = b[m];
        for (int j = 0; j < d; j++) {
            g += a[m][j] * x[j];
        }

        int h = (int) Math.floor(g / w);
        if (h < 0) {
            h += 2147483647;
        }

        return h;
    }

    /**
     * Apply hash functions on given vector x.
     * @param r universal hashing random integers.
     * @param x the vector to be hashed.
     * @return the bucket of hash table for given vector x.
     */
    private int hash(int[] r, double[] x) {
        long g = 0;
        for (int i = 0; i < k; i++) {
            g += r[i] * hash(x, i);
        }

        int h = (int) (g % P);
        if (h < 0) {
            h += P;
        }

        return h;
    }

    /**
     * Insert an item into the hash table.
     */
    public void add(int index, double[] x) {
        int bucket = hash(r2, x);
        int i = hash(r1, x) % H;

        if (table[i] == null) {
            table[i] = new HashEntry();
        }

        table[i].add(bucket, index);
    }

    /**
     * Returns the bucket entry for the given point.
     */
    public Bucket get(double[] x) {
        int bucket = hash(r2, x);
        int i = hash(r1, x) % H;

        HashEntry he = table[i];
        if (he == null) {
            return null;
        }

        for (Bucket be : he.buckets()) {
            if (bucket == be.bucket) {
                return be;
            }
        }

        return null;
    }
}
