/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vq;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static smile.util.Regex.DOUBLE_REGEX;
import static smile.util.Regex.INTEGER_REGEX;

/**
 * The neighborhood function for 2-dimensional lattice topology (e.g. SOM).
 * It determines the rate of change around the winner neuron.
 *
 * @author Haifeng Li
 */
public interface Neighborhood extends Serializable {
    /**
     * Returns the changing rate of neighborhood at a given iteration.
     * @param i the row distance of topology from the winner neuron.
     * @param j the column distance of topology from the winner neuron.
     * @param t the order number of current iteration.
     * @return the changing rate of neighborhood.
     */
    double of(int i, int j, int t);

    /**
     * The square neighborhood function. The neighbors which are in given
     * square width for both coordinates will be updated. The If width is 1,
     * only the winning neuron will be updated.
     * The square neighborhood function is a constant function in the defined
     * neighborhood of the winner neuron, that is, every neuron in the
     * neighborhood is updated the same proportion of the difference
     * between the neuron and the presented sample vector.
     *
     * @param width the width of neighborhood.
     * @return the square neighborhood function.
     */
    static Neighborhood square(int width) {
        return (i, j, t) -> Math.abs(i) < width && Math.abs(j) < width ? 1 : 0;
    }

    /**
     * The bubble neighborhood function. The neighbors which Euclidean distance
     * is less than or equal to the radius will be updated.
     * The bubble neighborhood function is a constant function in the defined
     * neighborhood of the winner neuron, that is, every neuron in the
     * neighborhood is updated the same proportion of the difference
     * between the neuron and the presented sample vector.
     * The bubble neighborhood function is a good compromise between the
     * computational cost and the approximation of the Gaussian.
     *
     * @param radius the radius of neighborhood.
     * @return the bubble neighborhood function.
     */
    static Neighborhood bubble(int radius) {
        return (i, j, t) -> Math.sqrt(i*i + j*j) <= radius ? 1 : 0;
    }

    /**
     * The triangle neighborhood function. The function decays linearly
     * from the BMU to a set radius, serving as an efficient alternative
     * to Gaussian function.
     * @param sigma the initial radius of neighborhood.
     * @param T the number of iterations.
     * @return Triangle neighborhood function.
     */
    static Neighborhood triangle(double sigma, double T) {
        return (i, j, t) -> {
            double s = sigma * Math.exp(-t / T);
            return Math.max(0, 1 - Math.sqrt(i*i + j*j) / s);
        };
    }

    /**
     * The Gaussian neighborhood function.
     * @param sigma the initial radius of neighborhood.
     * @param T the number of iterations.
     * @return Gaussian neighborhood function.
     */
    static Neighborhood Gaussian(double sigma, double T) {
        return (i, j, t) -> {
            double s = sigma * Math.exp(-t / T);
            double gamma = -0.5 / (s * s);
            return Math.exp(gamma * (i*i + j*j));
        };
    }

    /**
     * The Mexican hat (aka Ricker wave) neighborhood function. Mexican hat
     * penalizes neighbors that are a little farther away from the center.
     * If the model seeks to penalize near misses, Mexican hat function is
     * a good choice.
     * @param sigma the initial radius of neighborhood.
     * @param T the number of iterations.
     * @return Mexican hat neighborhood function.
     */
    static Neighborhood MexicanHat(double sigma, double T) {
        return (i, j, t) -> {
            double s = sigma * Math.exp(-t / T);
            double gamma = -0.5 / (s * s);
            double norm = gamma * (i*i + j*j);
            return (1 + norm / 2) * Math.exp(norm);
        };
    }

    /**
     * Parses a neighborhood function specification string.
     *
     * @param neighborhood the neighborhood function representation, e.g.
     *                     {@code "bubble(2)"}, {@code "square(2)"},
     *                     {@code "triangle(1.0, 1000)"}, {@code "Gaussian(1.0, 1000)"},
     *                     or {@code "MexicanHat(1.0, 1000)"}.
     * @return the neighborhood function.
     */
    static Neighborhood of(String neighborhood) {
        String s = neighborhood.trim().toLowerCase(Locale.ROOT);

        Pattern square = Pattern.compile(String.format("square\\((%s)\\)", INTEGER_REGEX));
        Matcher m = square.matcher(s);
        if (m.matches()) {
            return square(Integer.parseInt(m.group(1)));
        }

        Pattern bubble = Pattern.compile(String.format("bubble\\((%s)\\)", INTEGER_REGEX));
        m = bubble.matcher(s);
        if (m.matches()) {
            return bubble(Integer.parseInt(m.group(1)));
        }

        Pattern triangle = Pattern.compile(String.format("triangle\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
        m = triangle.matcher(s);
        if (m.matches()) {
            return triangle(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
        }

        Pattern gaussian = Pattern.compile(String.format("gaussian\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
        m = gaussian.matcher(s);
        if (m.matches()) {
            return Gaussian(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
        }

        Pattern mexicanHat = Pattern.compile(String.format("mexican_?hat\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
        m = mexicanHat.matcher(s);
        if (m.matches()) {
            return MexicanHat(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
        }

        throw new IllegalArgumentException("Unknown neighborhood function: " + neighborhood);
    }
}
