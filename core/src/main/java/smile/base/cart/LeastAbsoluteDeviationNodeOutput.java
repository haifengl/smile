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
 * Calculates the node output for LAD regression.
 * This is a special case of quantile regression of q = 0.5.
 */
public class LeastAbsoluteDeviationNodeOutput implements RegressionNodeOutput {

    /**
     * Residuals to fit.
     */
    private double[] residual;

    /**
     * Constructor.
     * @param residual response to fit.
     */
    public LeastAbsoluteDeviationNodeOutput(double[] residual) {
        this.residual = residual;
    }

    @Override
    public double calculate(int[] nodeSamples, int[] sampleCount) {
        double[] r = Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray();
        return QuickSelect.median(r);
    }
}