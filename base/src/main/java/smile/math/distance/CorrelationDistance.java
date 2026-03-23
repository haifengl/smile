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
package smile.math.distance;

import smile.math.MathEx;

import java.io.Serial;
import java.util.function.ToDoubleBiFunction;

/**
 * Correlation distance is defined as 1 - correlation coefficient.
 *
 * @author Haifeng Li
 */
public class CorrelationDistance implements Distance<double[]> {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The type of correlation is employed. */
    private final String method;
    /** Correlation lambda. */
    private final ToDoubleBiFunction<double[], double[]> cor;

    /**
     * Constructor of Pearson correlation distance.
     */
    public CorrelationDistance() {
        this("pearson");
    }

    /**
     * Constructor.
     * @param method the type of correlation.
     */
    public CorrelationDistance(String method) {
        this.method = method.trim().toLowerCase();
        switch (this.method) {
            case "pearson":
                cor = (x, y) -> 1 - MathEx.cor(x, y);
                break;
            case "spearman":
                cor = (x, y) -> 1 - MathEx.spearman(x, y);
                break;
            case "kendall":
                cor = (x, y) -> 1 - MathEx.kendall(x, y);
                break;
            default:
                throw new IllegalArgumentException("Invalid correlation: " + method);
        }

    }

    @Override
    public String toString() {
        return String.format("Correlation Distance(%s)", method);
    }

    /**
     * Pearson correlation  distance between the two arrays of type double.
     */
    @Override
    public double d(double[] x, double[] y) {
        return cor.applyAsDouble(x, y);
    }
}
