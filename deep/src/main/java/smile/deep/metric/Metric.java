/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.metric;

import smile.deep.tensor.Tensor;

/**
 * The class metrics keeps track of metric states, which enables them to
 * be able to calculate values through accumulations and synchronizations
 * across multiple processes.
 *
 * @author Haifeng Li
 */
public interface Metric {
    /**
     * Returns the name of metric.
     * @return the name of metric.
     */
    String name();

    /**
     * Updates the metric states with input data. This is often used when
     * new data needs to be added for metric computation.
     *
     * @param output the model output.
     * @param target the ground truth.
     */
    void update(Tensor output, Tensor target);

    /**
     * Computes the metric value from the metric state, which are updated by
     * previous update() calls. The compute frequency can be less than the
     * update frequency.
     * @return the metric value.
     */
    double compute();

    /**
     * Resets the metric state variables to their default value. Usually
     * this is called at the end of every epoch to clean up metric states.
     */
    void reset();
}
