/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.distance;

import java.io.Serializable;
import java.util.Iterator;
import smile.math.SparseArray;

/**
 * Minkowski distance of order p or L<sub>p</sub>-norm, is a generalization of
 * Euclidean distance that is actually L<sub>2</sub>-norm. You may also provide
 * a specified weight vector. For float or double arrays, missing values (i.e. NaN)
 * are also handled. Also support sparse arrays of which zeros are excluded
 * to save space.
 *
 * @author Haifeng Li
 */
public class SparseMinkowskiDistance implements Metric<SparseArray>, Serializable {
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
        return String.format("Minkowski distance (p = %d)", p);
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
