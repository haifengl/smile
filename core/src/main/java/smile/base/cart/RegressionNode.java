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

import java.math.BigInteger;
import java.util.List;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * A leaf node in regression tree.
 */
public class RegressionNode extends LeafNode {
    private static final long serialVersionUID = 2L;

    /** The mean of response variable. */
    private double mean;

    /**
     * The predicted output. In standard regression tree,
     * this is same as the mean. However, in gradient tree
     * boosting, this may be different.
     */
    private double output;

    /** The residual sum of squares. */
    private double rss;

    /**
     * Constructor.
     *
     * @param size the number of samples in the node
     * @param output the predicted value for this node.
     * @param mean the mean of response variable.
     * @param rss the residual sum of squares.
     */
    public RegressionNode(int size, double output, double mean, double rss) {
        super(size);
        this.output = output;
        this.mean = mean;
        this.rss = rss;
    }

    /** Returns the predicted value. */
    public double output() {
        return output;
    }

    /** Returns the mean of response variable. */
    public double mean() {
        return mean;
    }

    /** Returns the residual sum of squares. */
    public double impurity() {
        return rss;
    }

    @Override
    public double deviance() {
        return rss;
    }

    @Override
    public String dot(StructType schema, StructField response, int id) {
        return String.format(" %d [label=<%s = %.4f<br/>size = %d<br/>deviance = %.4f>, fillcolor=\"#00000000\", shape=ellipse];\n", id, response.name, output, size, rss);
    }

    @Override
    public int[] toString(StructType schema, StructField response, InternalNode parent, int depth, BigInteger id, List<String> lines) {
        StringBuilder line = new StringBuilder();

        // indent
        for (int i = 0; i < depth; i++) line.append(" ");
        line.append(id).append(") ");

        // split
        line.append(parent == null ? "root" : parent.toString(schema, this == parent.trueChild)).append(" ");

        // size
        line.append(size).append(" ");

        // deviance
        line.append(String.format("%.5g", rss)).append(" ");

        // fitted value
        line.append(String.format("%g", output));

        // terminal node
        line.append(" *");
        lines.add(line.toString());

        return new int[]{size};
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
