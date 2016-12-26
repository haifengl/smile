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
public class LeeDistance implements Metric<int[]>, Serializable {
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
        return String.format("Lee distance (q = %d)", q);
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
