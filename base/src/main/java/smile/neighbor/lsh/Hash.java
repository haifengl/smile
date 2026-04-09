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
package smile.neighbor.lsh;

import java.io.Serial;
import java.io.Serializable;
import smile.math.MathEx;

/**
 * The hash function for Euclidean spaces.
 *
 * @author Haifeng Li
 */
public class Hash implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The range of universal hashing random integers [0, 2<sup>29</sup>).
     */
    final int MAX_HASH_RND = 536870912;
    /**
     * The prime number in universal bucket hashing.
     */
    final int P = 2147483647;
    /**
     * The size of hash table.
     */
    int H;
    /**
     * The dimensionality of data.
     */
    int d;
    /**
     * The number of random projections per hash value.
     */
    int k;
    /**
     * The width of projection. The hash function is defined as floor((a * x + b) / w). The value
     * of w determines the bucket interval.
     */
    double w;
    /**
     * The random integer used for universal bucket hashing.
     */
    int[] c;

    /**
     * The random projection matrix. Each row a[i] is a k-dimensional random
     * vector with entries drawn independently from a Gaussian distribution.
     * Stored as a plain double[][] for cache-friendly, allocation-free dot
     * products (avoids the DenseMatrix/Vector overhead).
     */
    double[][] a;
    /**
     * Real numbers chosen uniformly from the range [0, w].
     */
    double[] b;

    /**
     * Hash table.
     */
    Bucket[] table;

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param k the number of random projection hash functions, which is usually
     *          set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently away
     *          from 0. But we should not choose a w value that is too large,
     *          which will increase the query time.
     * @param H the size of universal hash tables.
     */
    public Hash(int d, int k, double w, int H) {
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

        // Build plain double[][] projection matrix (k rows, d columns).
        // Fill in column-major order (outer=col, inner=row) to match the
        // original DenseMatrix.randn which drew values column by column.
        var norm = smile.stat.distribution.GaussianDistribution.getInstance();
        a = new double[k][d];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < k; i++) {
                a[i][j] = norm.rand();
            }
        }

        b = new double[k];
        for (int i = 0; i < k; i++) {
            b[i] = MathEx.random(0, w);
        }

        c = new int[k];
        for (int i = 0; i < k; i++) {
            c[i] = MathEx.randomInt(MAX_HASH_RND);
        }

        table = new Bucket[H];
    }

    /**
     * Returns the raw hash value of given vector x for the i-th hash function.
     * Computes (dot(a[i], x) + b[i]) / w without any heap allocation.
     *
     * @param x the vector to be hashed.
     * @param i the i-th hash function to be employed.
     * @return the raw hash value.
     */
    double hash(double[] x, int i) {
        double[] ai = a[i];
        double g = b[i];
        for (int j = 0; j < d; j++) {
            g += ai[j] * x[j];
        }
        return g / w;
    }

    /**
     * Apply hash functions on given vector x and return the universal bucket index.
     * Uses the pre-allocated row arrays directly — no Vector allocation.
     * @param x the vector to be hashed.
     * @return the bucket of hash table for given vector x.
     */
    public int hash(double[] x) {
        long g = 0;
        for (int i = 0; i < k; i++) {
            double[] ai = a[i];
            double dot = b[i];
            for (int j = 0; j < d; j++) {
                dot += ai[j] * x[j];
            }
            int hi = (int) Math.floor(dot / w);
            g += (long) c[i] * hi;
        }

        int gint = (int) (g % P);
        return gint >= 0 ? gint : gint + P;
    }

    /**
     * Insert an item into the hash table.
     * @param index the index of point in the data set.
     * @param x the data point.
     */
    public void add(int index, double[] x) {
        int bucket = hash(x);
        int i = bucket % H;

        if (table[i] == null) {
            table[i] = new Bucket(bucket);
        }

        table[i].add(index);
    }

    /**
     * Returns the bucket entry for the given hash value.
     * @param i the hash code.
     * @return the bucket.
     */
    public Bucket get(int i) {
        return table[i % H];
    }

    /**
     * Returns the bucket entry for the given point.
     * @param x the data point.
     * @return the bucket.
     */
    public Bucket get(double[] x) {
        return table[hash(x) % H];
    }
}
