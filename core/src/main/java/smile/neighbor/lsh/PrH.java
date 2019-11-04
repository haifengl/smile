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

/**
 * Probability for given query object and hash function.
 */
public class PrH implements Comparable<PrH>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The index of bucket.
     */
    public final int u;
    /**
     * The probability
     */
    public final double pr;

    /** Constructor. */
    public PrH(int u, double pr) {
        this.u = u;
        this.pr = pr;
    }

    @Override
    public int compareTo(PrH o) {
        // to sort PrH in decreasing order.
        return Double.compare(o.pr, pr);
    }
}

