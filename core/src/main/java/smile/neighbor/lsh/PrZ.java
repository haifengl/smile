/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.neighbor.lsh;

/**
 * The probability list of all buckets for given query object.
 *
 * @author Haifeng Li
 */
public class PrZ implements Comparable<PrZ> {

    /**
     * The index of hash function.
     */
    public final int m;
    /**
     * The n<sub>i</sub> probabilities for h<sub>m</sub> hash function,
     * where n<sub>i</sub> = u<sub>i_max</sub> - u<sub>i_min</sub> + 1.
     */
    public final PrH[] prh;

    /**
     * Constructor.
     * @param m the index of hash function.
     * @param prh the n<sub>i</sub> probabilities for h<sub>m</sub> hash function.
     */
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

