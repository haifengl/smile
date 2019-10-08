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
 * Calculates the node output for two-class logistic regression.
 */
public class BinaryLogisticRegressionNodeOutput implements RegressionNodeOutput {

    /**
     * Pseudo response to fit.
     */
    double[] y;
    /**
     * Constructor.
     * @param y pseudo response to fit.
     */
    public BinaryLogisticRegressionNodeOutput(double[] y) {
        this.y = y;
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        double nu = 0.0;
        double de = 0.0;
        for (int i : nodeSamples) {
            double abs = Math.abs(y[i]);
            nu += y[i];
            de += abs * (2.0 - abs);
        }

        return nu / de;
    }
}