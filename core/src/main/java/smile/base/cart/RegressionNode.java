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

    /** The residual sum of squares. */
    private double rss;

    /**
     * Constructor.
     *
     * @param size the number of samples in the node
     * @param output the predicted value for this node.
     * @param rss the residual sum of squares.
     */
    public RegressionNode(int size, double output, double rss) {
        super(size);
        this.output = output;
        this.rss = rss;
    }

    /** Returns the predicted value. */
    public double output() {
        return output;
    }

    /**
     * Adds some samples of class to the node.
     * @param y the responsible value
     * @param s the number of samples.
     */
    public void add(double y, int s) {
        size += s;
        output += y * s;
        rss += y * y * s;
    }

    /** Calculates the output. */
    public void calculateOutput() {
        output /= size;
        rss -= size * output * output;
    }

    /**
     * Returns the residual sum of squares.
     */
    public double impurity() {
        return rss;
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
