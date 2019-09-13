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

import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * A leaf node in regression tree.
 */
public class RegressionNode extends LeafNode {
    private static final long serialVersionUID = 1L;

    /** The predicted output. */
    private double output;

    /** The squared error. */
    private double error;

    /**
     * Constructor.
     *
     * @param size the number of samples in the node
     * @param output the predicted value for this node.
     * @param error the squared error.
     */
    public RegressionNode(int size, double output, double error) {
        super(size);
        this.output = output;
        this.error = error;
    }

    /** Returns the predicted value. */
    public double output() {
        return output;
    }

    /**
     * Returns the squared error.
     */
    public double impurity() {
        return error;
    }

    @Override
    public String toDot(StructType schema, int id) {
        return String.format(" %d [label=<%.4f>, fillcolor=\"#00000000\", shape=ellipse];\n", id, output);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegressionNode) {
            RegressionNode a = (RegressionNode) o;
            return MathEx.equals(output, a.output);
        }

        return false;
    }
}
