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

/**
 * In coding theory, the Lee distance is a distance between two strings
 * x<sub>1</sub>x<sub>2</sub>...x<sub>n</sub> and y<sub>1</sub>y<sub>2</sub>...y<sub>n</sub>
 * of equal length n over the q-ary alphabet {0,1,...,q-1} of size q &ge; 2, defined as
 * <p>
 * sum min(|x<sub>i</sub>-y<sub>i</sub>|, q-|x<sub>i</sub>-y<sub>i</sub>|)
 * <p>
 * If q = 2 or q = 3 the Lee distance coincides with the Hamming distance.
 * @author Haifeng Li
 */
public class LeeDistance implements Metric<int[]> {
    private static final long serialVersionUID = 1L;

    private int q;

    /**
     * Constructor with a given size q of alphabet.
     * @param q the size of q-ary alphabet.
     */
    public LeeDistance(int q) {
        if (q < 2)
            throw new IllegalArgumentException(String.format("The size of q-ary alphabet has to be larger than 1: q = %d", q));

        this.q = q;
    }

    @Override
    public String toString() {
        return String.format("Lee Distance(q = %d)", q);
    }

    @Override
    public double d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int dist = 0;
        for (int i = 0; i < x.length; i++) {
            double d = Math.abs(x[i] - y[i]);
            dist += Math.min(d, q-d);
        }

        return dist;
    }
}
