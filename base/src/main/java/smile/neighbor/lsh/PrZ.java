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

/**
 * The probability list of all buckets for given query object.
 *
 * @param m the index of hash function.
 * @param prh the n<sub>i</sub> probabilities for h<sub>m</sub> hash function.
 * @author Haifeng Li
 */
public record PrZ(int m, PrH[] prh) implements Comparable<PrZ> {

    @Override
    public int compareTo(PrZ o) {
        // to sort PrZ in decreasing order.
        return prh[0].compareTo(o.prh[0]);
    }
}

