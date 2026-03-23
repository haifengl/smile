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
package smile.validation.metric;

import java.io.Serializable;
import java.util.function.ToDoubleBiFunction;

/**
 * An abstract interface to measure the regression performance.
 *
 * @author Haifeng Li
 */
public interface RegressionMetric extends ToDoubleBiFunction<double[], double[]>, Serializable {
    /**
     * Returns a score to measure the quality of regression.
     * @param truth the true response values.
     * @param prediction the predicted response values.
     * @return the metric.
     */
    double score(double[] truth, double[] prediction);

    @Override
    default double applyAsDouble(double[]  truth, double[] prediction) {
        return score(truth, prediction);
    }
}
