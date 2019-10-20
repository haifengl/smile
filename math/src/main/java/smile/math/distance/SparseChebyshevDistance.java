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

import java.util.Iterator;
import smile.util.SparseArray;

/**
 * Chebyshev distance (or Tchebychev distance), or L<sub>&infin;</sub> metric
 * is a metric defined on a vector space where the distance between two vectors
 * is the greatest of their differences along any coordinate dimension.
 * 
 * @author Haifeng Li
 */
public class SparseChebyshevDistance implements Metric<SparseArray> {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public SparseChebyshevDistance() {
    }

    @Override
    public String toString() {
        return "Chebyshev Distance";
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

        while (a != null && b != null) {
            if (a.i < b.i) {
                double d = Math.abs(a.x);
                if (dist < d)
                    dist = d;

                a = iterX.hasNext() ? iterX.next() : null;
            } else if (a.i > b.i) {
                double d = Math.abs(b.x);
                if (dist < d)
                    dist = d;

                b = iterY.hasNext() ? iterY.next() : null;
            } else {
                double d = Math.abs(a.x - b.x);
                if (dist < d)
                    dist = d;

                a = iterX.hasNext() ? iterX.next() : null;
                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        while (a != null) {
            double d = Math.abs(a.x);
            if (dist < d)
                dist = d;

            a = iterX.hasNext() ? iterX.next() : null;
        }

        while (b != null) {
            double d = Math.abs(b.x);
            if (dist < d)
                dist = d;

            b = iterY.hasNext() ? iterY.next() : null;
        }

        return Math.sqrt(dist);
    }
}
