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
 * Calculates the node output for quantile regression.
 * Quantile regression models the relation between a set of predictor
 * variables and specific percentiles (or quantiles) of the response
 * variable. It specifies changes in the quantiles of the response.
 * <p>
 * The most popular quantile is the median, or the 50th percentile,
 * and in this case the quantile loss is simply the sum of absolute errors.
 * Other quantiles could give endpoints of a prediction interval;
 * for example a middle-80-percent range is defined by the 10th and
 * 90th percentiles. The quantile loss differs depending on the evaluated
 * quantile, such that more negative errors are penalized more for higher
 * quantiles and more positive errors are penalized more for lower quantiles.
 * <p>
 * The gradient tree boosting based on this loss function
 * is highly robust. The trees use only order information
 * on the input variables and the pseudo-response has only
 * two values {-1, +1}. The line searches (terminal node values)
 * use only specified quantile ratio.
 */
public class QuantileNodeOutput implements RegressionNodeOutput {
    /**
     * The percentile.
     */
    private double q = 0.5;

    /**
     * Residuals to fit.
     */
    private double[] residual;

    /**
     * Constructor.
     * @param q the percentile.
     * @param residual response to fit.
     */
    public QuantileNodeOutput(double q, double[] residual) {
        if (q <= 0.0 || q >= 1.0) {
            throw new IllegalArgumentException("Invalid quantile: " + q);
        }

        this.q = q;
        this.residual = residual;
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        double[] r = Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray();
        int p = (int) (r.length * q);
        return QuickSelect.select(r, p);
    }
}