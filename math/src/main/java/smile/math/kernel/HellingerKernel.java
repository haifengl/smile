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

package smile.math.kernel;

/**
 * The Hellinger Mercer Kernel.

 * @author Diego Catalano
 */
public class HellingerKernel implements MercerKernel<double[]> {
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructor.
     */
    public HellingerKernel() {}

    @Override
    public String toString() {
        return "Hellinger Kernel";
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += Math.sqrt(x[i] * y[i]);
        }

        return sum;
    }
}