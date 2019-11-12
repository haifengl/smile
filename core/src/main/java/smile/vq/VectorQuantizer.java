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
 * Vector quantizer with competitive learning.
 *
 * @author Haifeng Li
 */
public interface VectorQuantizer extends Serializable {
    /**
     *  The label for outliers or noises.
     */
    int OUTLIER = Integer.MAX_VALUE;

    /**
     * Update the codebook with a new observation.
     */
    void update(double[] x);

    /**
     * Quantize a new observation. Returns Optional.empty
     * if the observation is noise.
     * @param x a new observation.
     */
    double[] quantize(double[] x);
}
