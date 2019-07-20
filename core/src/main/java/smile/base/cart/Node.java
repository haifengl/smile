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

package smile.base.cart;

import java.io.Serializable;

/**
 * CART tree node.
 */
public abstract class Node implements Serializable {
    /** The id of node. */
    int id;

    /**
     * Predicted real value for this node.
     */
    double output = 0.0;

    /**
     * Constructor.
     * @param output the predicted value for this node.
     */
    public Node(int id, double output) {
        this.id = id;
        this.output = output;
    }

    /**
     * Evaluate the regression tree over an instance.
     */
    public abstract double predict(double[] x);

    /**
     * Returns a dot representation for visualization.
     */
    public abstract String toDot();

    /**
     * Returns the maximum depth of the tree -- the number of
     * nodes along the longest path from this node
     * down to the farthest leaf node.
     */
    public abstract int depth();
}

