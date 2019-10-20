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

package smile.math.distance;

import java.util.Arrays;
import java.util.Iterator;
import smile.util.SparseArray;

/**
 * Minkowski distance of order p or L<sub>p</sub>-norm, is a generalization of
 * Euclidean distance that is actually L<sub>2</sub>-norm. You may also provide
 * a specified weight vector. For float or double arrays, missing values (i.e. NaN)
 * are also handled. Also support sparse arrays of which zeros are excluded
 * to save space.
 *
 * @author Haifeng Li
 */
public class SparseMinkowskiDistance implements Metric<SparseArray> {
    private static final long serialVersionUID = 1L;

    /**
     * The order of Minkowski distance.
     */
    private int p;

    /**
     * The weights used in weighted distance.
     */
    private double[] weight = null;

    /**
     * Constructor.
     */
    public SparseMinkowskiDistance(int p) {
        if (p <= 0)
            throw new IllegalArgumentException(String.format("The order p has to be larger than 0: p = d", p));

        this.p = p;
    }

    /**
     * Constructor.
     *
     * @param weight the weight vector.
     */
    public SparseMinkowskiDistance(int p, double[] weight) {
        if (p <= 0)
            throw new IllegalArgumentException(String.format("The order p has to be larger than 0: p = d", p));

        for (int i = 0; i < weight.length; i++) {
            if (weight[i] < 0)
                throw new IllegalArgumentException(String.format("Weight has to be nonnegative: %f", weight[i]));
        }

        this.p = p;
        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null)
            return String.format("Weighted Minkowski Distance(p = %d, weight = %s)", p, Arrays.toString(weight));
        else
            return String.format("Minkowski Distance(p = %d)", p);
    }

    @Override
    public double d(SparseArray x, SparseArray y) {
        if (x.isEmpty())
            throw new IllegalArgumentException("List x is empty.");

        if (y.isEmpty())
            throw new IllegalArgumentException("List y is empty.");

        Iterator<SparseArray.Entry> iterX = x.iterator();
        Iterator<SparseArray.Entry> iterY = y.iterator();

        SparseArray.Entry a = iterX.hasNext() ? iterX.next() : null;
        SparseArray.Entry b = iterY.hasNext() ? iterY.next() : null;

        double dist = 0.0;

        if (weight == null) {
            while (a != null && b != null) {
                if (a.i < b.i) {
                    double d = a.x;
                    dist += Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    double d = b.x;
                    dist += Math.pow(d, p);

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.x - b.x;
                    dist += Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.x;
                dist += Math.pow(d, p);

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.x;
                dist += Math.pow(d, p);

                b = iterY.hasNext() ? iterY.next() : null;
            }
        } else {
            while (a != null && b != null) {
                if (a.i < b.i) {
                    double d = a.x;
                    dist += weight[a.i] * Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    double d = b.x;
                    dist += weight[b.i] * Math.pow(d, p);

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.x - b.x;
                    dist += weight[a.i] * Math.pow(d, p);

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.x;
                dist += weight[a.i] * Math.pow(d, p);

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.x;
                dist += weight[b.i] * Math.pow(d, p);

                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        return Math.pow(dist, 1.0/p);
    }
}
