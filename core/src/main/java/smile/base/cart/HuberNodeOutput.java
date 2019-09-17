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

import java.util.Arrays;
import smile.sort.QuickSelect;

/**
 * Calculates the node output for Huber regression.
 */
public class HuberNodeOutput implements RegressionNodeOutput {

    /**
     * Residuals.
     */
    private double[] residual;
    /**
     * Responses to fit.
     */
    private double[] response;
    /**
     * The value to choose &alpha;-quantile of residuals.
     */
    private double alpha;
    /**
     * Cutoff.
     */
    private double delta;

    /**
     * Constructor.
     * @param residual residuals.
     * @param response the responses to fit.
     * @param alpha the value to choose &alpha;-quantile of residuals.
     */
    public HuberNodeOutput(double[] residual, double[] response, double alpha) {
        this.residual = residual;
        this.response = response;
        this.alpha = alpha;

        int n = residual.length;
        for (int i = 0; i < n; i++) {
            response[i] = Math.abs(residual[i]);
        }

        delta = QuickSelect.select(response, (int) (n * alpha));

        for (int i = 0; i < n; i++) {
            if (Math.abs(residual[i]) <= delta) {
                response[i] = residual[i];
            } else {
                response[i] = delta * Math.signum(residual[i]);
            }
        }
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        double r = QuickSelect.median(Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray());
        double output = 0.0;
        for (int i : nodeSamples) {
            double d = residual[i] - r;
            output += Math.signum(d) * Math.min(delta, Math.abs(d));
        }

        output = r + output / nodeSamples.length;
        return output;
    }

}