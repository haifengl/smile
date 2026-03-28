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
package smile.math.distance;

import java.io.Serial;
import java.util.Arrays;
import java.util.Iterator;
import smile.util.SparseArray;

/**
 * Minkowski distance of order p or L<sub>p</sub>-norm, is a generalization of
 * Euclidean distance that is actually L<sub>2</sub>-norm. You may also provide
 * a specified weight vector.
 *
 * @author Haifeng Li
 */
public class SparseMinkowskiDistance implements Metric<SparseArray> {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The order of Minkowski distance.
     */
    private final int p;

    /**
     * The weights used in weighted distance.
     */
    private final double[] weight;

    /**
     * Constructor.
     * @param p the order of Minkowski distance.
     */
    public SparseMinkowskiDistance(int p) {
        this(p, null);
    }

    /**
     * Constructor.
     * @param p the order of Minkowski distance.
     * @param weight the weight vector.
     */
    public SparseMinkowskiDistance(int p, double[] weight) {
        if (p <= 0) {
            throw new IllegalArgumentException(String.format("The order p has to be larger than 0: p = %d", p));
        }

        if (weight != null) {
            for (double w : weight) {
                if (w < 0) {
                    throw new IllegalArgumentException(String.format("Weight has to be non-negative: %f", w));
                }
            }
        }

        this.p = p;
        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null) {
            return String.format("Weighted Minkowski Distance(p = %d, weight = %s)", p, Arrays.toString(weight));
        } else {
            return String.format("Minkowski Distance(p = %d)", p);
        }
    }

    @Override
    public double d(SparseArray x, SparseArray y) {
        if (x.isEmpty()) {
            throw new IllegalArgumentException("List x is empty.");
        }

        if (y.isEmpty()) {
            throw new IllegalArgumentException("List y is empty.");
        }

        Iterator<SparseArray.Entry> iterX = x.iterator();
        Iterator<SparseArray.Entry> iterY = y.iterator();

        SparseArray.Entry a = iterX.hasNext() ? iterX.next() : null;
        SparseArray.Entry b = iterY.hasNext() ? iterY.next() : null;

        double dist = 0.0;

        if (weight == null) {
            while (a != null && b != null) {
                if (a.index() < b.index()) {
                    double d = a.value();
                    dist += Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.index() > b.index()) {
                    double d = b.value();
                    dist += Math.pow(d, p);

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.value() - b.value();
                    dist += Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.value();
                dist += Math.pow(d, p);

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.value();
                dist += Math.pow(d, p);

                b = iterY.hasNext() ? iterY.next() : null;
            }
        } else {
            while (a != null && b != null) {
                if (a.index() < b.index()) {
                    double d = a.value();
                    dist += weight[a.index()] * Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.index() > b.index()) {
                    double d = b.value();
                    dist += weight[b.index()] * Math.pow(d, p);

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.value() - b.value();
                    dist += weight[a.index()] * Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.value();
                dist += weight[a.index()] * Math.pow(d, p);

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.value();
                dist += weight[b.index()] * Math.pow(d, p);

                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        return Math.pow(dist, 1.0/p);
    }
}
