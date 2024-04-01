/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.deep.activation;

import java.io.Serializable;
import java.util.function.Function;
import smile.deep.tensor.Tensor;

/**
 * The non-linear activation function.
 *
 * @author Haifeng Li
 */
public interface ActivationFunction extends
        Function<org.bytedeco.pytorch.Tensor, org.bytedeco.pytorch.Tensor>,
        Serializable {
    /**
     * Returns the name of activation function.
     * @return the name of activation function.
     */
    String name();

    /**
     * Applies this function to the given argument.
     * @param x a tensor.
     * @return the output tensor.
     */
    default Tensor apply(Tensor x) {
        return Tensor.of(apply(x.asTorch()));
    }
}
