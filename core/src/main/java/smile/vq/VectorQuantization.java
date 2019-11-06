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
import java.util.Optional;

/**
 * Originally used for data compression, Vector quantization (VQ)
 * allows the modeling of probability density functions by
 * the distribution of prototype vectors. It works by dividing
 * a large set of points (vectors) into groups having approximately
 * the same number of points closest to them. Each group is represented
 * by its centroid point, as in K-Means and some other clustering algorithms.
 * <p>
 * Vector quantization is is based on the competitive learning paradigm,
 * and also closely related to sparse coding models
 * used in deep learning algorithms such as autoencoder.
 *
 * @author Haifeng Li
 */
public interface VectorQuantization extends Serializable {
    /**
     *  The label for outliers or noises.
     */
    int OUTLIER = Integer.MAX_VALUE;

    /**
     * Quantize a new observation. Returns Optional.empty
     * if the observation is noise.
     * @param x a new observation.
     */
    Optional<double[]> quantize(double[] x);

    /**
     * Returns the index of nearest centroid in the code book.
     * @param x a new observation.
     */
    int code(double[] x);
}
