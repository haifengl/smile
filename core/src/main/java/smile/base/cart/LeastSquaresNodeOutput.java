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
 * Calculates the node output for least squares regression.
 */
public class LeastSquaresNodeOutput implements RegressionNodeOutput {

    /**
     * Responses to fit.
     */
    private double[] response;

    /**
     * Constructor.
     * @param response the responses to fit.
     */
    public LeastSquaresNodeOutput(double[] response) {
        this.response = response;
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        int n = 0;
        double output = 0.0;
        for (int i : nodeSamples) {
            n += sampleCount[i];
            output += response[i] * sampleCount[i];
        }

        return output / n;
    }
}