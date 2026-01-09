/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model.cart;

import java.io.Serial;
import java.math.BigInteger;
import java.util.List;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * A leaf node in regression tree.
 *
 * @author Haifeng Li
 */
public class RegressionNode extends LeafNode {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The mean of response variable. */
    private final double mean;

    /**
     * The predicted output. In standard regression tree,
     * this is same as the mean. However, in gradient tree
     * boosting, this may be different.
     */
    private final double output;

    /** The residual sum of squares. */
    private final double rss;

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

    /**
     * Returns the predicted value.
     * @return the predicted value.
     */
    public double output() {
        return output;
    }

    /**
     * Returns the mean of response variable.
     * @return the mean of response variable.
     */
    public double mean() {
        return mean;
    }

    /**
     * Returns the residual sum of squares.
     * @return the residual sum of squares.
     */
    public double impurity() {
        return rss;
    }

    @Override
    public double deviance() {
        return rss;
    }

    @Override
    public String dot(StructType schema, StructField response, int id) {
        return String.format(" %d [label=<%s = %.4f<br/>size = %d<br/>deviance = %.4f>, fillcolor=\"#00000000\", shape=ellipse];\n", id, response.name(), output, size, rss);
    }

    @Override
    public int[] toString(StructType schema, StructField response, InternalNode parent, int depth, BigInteger id, List<String> lines) {
        String line = " ".repeat(depth) + // indent
                id + ") " +
                (parent == null ? "root" : parent.toString(schema, this == parent.trueChild)) + " " + // split
                size + " " + // size
                String.format("%.5g", rss) + " " + // deviance
                String.format("%g", output) + // fitted value
                " *"; // terminal node
        lines.add(line);

        return new int[]{size};
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegressionNode a) {
            return MathEx.equals(output, a.output);
        }

        return false;
    }
}
