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
import smile.math.Math;

/**
 * The Jensen-Shannon divergence is a popular method of measuring the
 * similarity between two probability distributions. It is also known
 * as information radius or total divergence to the average.
 * <p>
 * The Jensen-Shannon divergence is a symmetrized and smoothed version of the
 * Kullback-Leibler divergence . It is defined by
 * <p>
 * J(P||Q) = (D(P||M) + D(Q||M)) / 2
 * <p>
 * where M = (P+Q)/2 and D(&middot;||&middot;) is KL divergence.
 * Different from the Kullback-Leibler divergence, it is always a finite value.
 * <p>
 * The square root of the Jensen-Shannon divergence is a metric, which is
 * calculated by this class.
 * 
 * @author Haifeng Li
 */
public class JensenShannonDistance implements Metric<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public JensenShannonDistance() {
    }

    @Override
    public String toString() {
        return "Jensen-Shannon distance";
    }

    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return Math.sqrt(Math.JensenShannonDivergence(x, y));
    }
}
