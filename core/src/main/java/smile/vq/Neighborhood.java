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

package smile.vq;

import java.io.Serializable;

/**
 * The neighborhood function for 2-dimensional lattice topology (e.g. SOM).
 * It determines the rate of change around the winner neuron.
 *
 * @author Haifeng Li
 */
public interface Neighborhood extends Serializable {
    /**
     * Returns the changing rate of neighborhood at a given iteration.
     * @param i the row distance of topology from the the winner neuron.
     * @param j the column distance of topology from the the winner neuron.
     * @param t the order number of current iteration.
     */
    double of(int i, int j, int t);

    /**
     * Returns the bubble neighborhood function.
     * The bubble neighborhood function is a constant function in the defined
     * neighborhood of the winner neuron, that is, every neuron in the
     * neighborhood is updated the same proportion of the difference
     * between the neuron and the presented sample vector.
     * The bubble neighborhood function is a good compromise between the
     * computational cost and the approximation of the Gaussian.
     *
     * @param radius the radius of neighborhood.
     */
    static Neighborhood bubble(int radius) {
        return (i, j, t) -> Math.abs(i) < radius && Math.abs(j) < radius ? 1 : 0;
    }

    /**
     * Returns Gaussian neighborhood function.
     * @param sigma the initial radius of neighborhood.
     * @param T the number of iterations.
     */
    static Neighborhood Gaussian(double sigma, double T) {
        return (i, j, t) -> {
            double s = sigma * Math.exp(-t / T);
            double gamma = -0.5 / (s * s);
            return Math.exp(gamma * (i*i + j*j));
        };
    }
}
