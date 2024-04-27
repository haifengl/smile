/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.distance;

import java.io.Serial;
import java.util.Arrays;
import java.util.Iterator;
import smile.util.SparseArray;

/**
 * Manhattan distance, also known as L<sub>1</sub> distance or L<sub>1</sub>
 * norm, is the sum of the (absolute) differences of their coordinates.
 *
 * @author Haifeng Li
 */
public class SparseManhattanDistance implements Metric<SparseArray> {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The weights used in weighted distance.
     */
    private double[] weight = null;

    /**
     * Constructor.
     */
    public SparseManhattanDistance() {
    }

    /**
     * Constructor.
     *
     * @param weight the weight vector.
     */
    public SparseManhattanDistance(double[] weight) {
        for (double w : weight) {
            if (w < 0) {
                throw new IllegalArgumentException(String.format("Weight has to be non-negative: %f", w));
            }
        }

        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null) {
            return String.format("Weighted Manhattan Distance(%s)", Arrays.toString(weight));
        } else {
            return "Manhattan Distance";
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
                if (a.i < b.i) {
                    dist += Math.abs(a.x);
                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    dist += Math.abs(b.x);
                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    dist += Math.abs(a.x - b.x);
                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                dist += Math.abs(a.x);
                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                dist += Math.abs(b.x);
                b = iterY.hasNext() ? iterY.next() : null;
            }
        } else {
            while (a != null && b != null) {
                if (a.i < b.i) {
                    dist += weight[a.i] * Math.abs(a.x);
                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    dist += weight[b.i] * Math.abs(b.x);
                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    dist += weight[a.i] * Math.abs(a.x - b.x);
                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                dist += weight[a.i] * Math.abs(a.x);
                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                dist += weight[b.i] * Math.abs(b.x);
                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        return Math.sqrt(dist);
    }
}
