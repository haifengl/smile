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
package smile.ica;

import java.io.Serial;
import java.io.Serializable;
import smile.util.function.DifferentiableFunction;

/**
 * The kurtosis of the probability density function of a signal.
 * Note that kurtosis is very sensitive to outliers.
 * <p>
 * The function is {@code G(u) = u⁴/4}.
 *
 * @author Haifeng Li
 */
public class Kurtosis implements DifferentiableFunction, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Constructor. */
    public Kurtosis() {
    }

    @Override
    public double f(double x) {
        return 0.25 * x * x * x * x;
    }

    @Override
    public double g(double x) {
        return x * x * x;
    }

    @Override
    public double g2(double x) {
        return 3.0 * x * x;
    }

    @Override
    public String toString() {
        return "Kurtosis";
    }
}
