/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.deep.optimizer;

import java.io.Serializable;
import smile.base.mlp.Layer;

/**
 * The neural network optimizer.
 *
 * @author Haifeng Li
 */
public interface Optimizer extends Serializable {
    /**
     * Updates a layer.
     * @param layer a neural network layer.
     * @param m the size of mini-batch.
     * @param t the time step, i.e. the number of training iterations so far.
     */
    void update(Layer layer, int m, int t);
}
