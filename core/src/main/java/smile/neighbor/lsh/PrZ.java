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

/**
 * Probability list of all buckets for given query object.
 */
public class PrZ implements Comparable<PrZ> {

    /**
     * The index of hash function.
     */
    public final int m;
    /**
     * The n_i probabilities for h_m hash function,
     * where n_i = u_i_max - u_i_min + 1.
     */
    public final PrH[] prh;

    /** Constructor. */
    public PrZ(int m, PrH[] prh) {
        this.m = m;
        this.prh = prh;
    }

    @Override
    public int compareTo(PrZ o) {
        // to sort PrZ in decreasing order.
        return prh[0].compareTo(o.prh[0]);
    }
}

