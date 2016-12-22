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
 * Manhattan distance, also known as L<sub>1</sub> distance or L<sub>1</sub>
 * norm, is the sum of the (absolute) differences of their coordinates. Use
 * getInstance() to get the standard unweighted Manhattan distance. Or create
 * an instance with a specified weight vector. For float or double arrays,
 * missing values (i.e. NaN) are also handled. Also support sparse arrays
 * of which zeros are excluded to save space.
 *
 * @author Haifeng Li
 */
public class SparseManhattanDistance implements Metric<SparseArray>, Serializable {
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
        for (int i = 0; i < weight.length; i++) {
            if (weight[i] < 0)
                throw new IllegalArgumentException(String.format("Weight has to be nonnegative: %f", weight[i]));
        }

        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null)
            return "Weighted Manhattan distance";
        else
            return "Manhattan distance";
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
