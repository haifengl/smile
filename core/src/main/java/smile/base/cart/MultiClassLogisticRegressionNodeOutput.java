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

package smile.base.cart;

/**
 * Calculates the node output for multi-class logistic regression.
 */
public class MultiClassLogisticRegressionNodeOutput implements RegressionNodeOutput {
    /** The number of classes. */
    int k;

    /** The responses to fit. */
    double[] y;

    /**
     * Constructor.
     * @param k the number of classes.
     * @param response response to fit.
     */
    public MultiClassLogisticRegressionNodeOutput(int k, double[] response) {
        this.k = k;
        this.y = response;
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        double nu = 0.0;
        double de = 0.0;
        for (int i : nodeSamples) {
            double abs = Math.abs(y[i]);
            nu += y[i];
            de += abs * (1.0 - abs);
        }

        if (de < 1E-10) {
            return nu / nodeSamples.length;
        }

        return ((k-1.0) / k) * (nu / de);
    }
}
