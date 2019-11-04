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

import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A leaf node in decision tree.
 */
public class DecisionNode extends LeafNode {
    private static final long serialVersionUID = 2L;

    /** The predicted output. */
    private int output;

    /** The number of node samples in each class. */
    private int[] count;

    /**
     * Constructor.
     *
     * @param count the number of node samples in each class.
     */
    public DecisionNode(int[] count) {
        super((int) MathEx.sum(count));
        this.output = MathEx.whichMax(count);
        this.count = count;
    }

    /** Returns the predicted value. */
    public int output() {
        return output;
    }

    /** Returns the number of node samples in each class. */
    public int[] count() {
        return count;
    }

    @Override
    public double deviance() {
        return deviance(count, posteriori(count, new double[count.length]));
    }

    @Override
    public String dot(StructType schema, StructField response, int id) {
        return String.format(" %d [label=<%s = %s<br/>size = %d<br/>deviance = %.4f>, fillcolor=\"#00000000\", shape=ellipse];\n", id, response.name, response.toString(output), size, deviance());
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
        double[] prob = posteriori(count, new double[count.length]);
        line.append(String.format("%.5g", deviance(count, prob))).append(" ");

        // fitted value
        line.append(response.toString(output)).append(" ");

        // probabilities
        line.append(Arrays.stream(prob).mapToObj(p -> String.format("%.5g", p)).collect(Collectors.joining(" ", "(", ")")));

        // terminal node
        line.append(" *");
        lines.add(line.toString());

        return count;
    }

    /**
     * Returns the impurity of node.
     * @return  the impurity of node
     */
    public double impurity(SplitRule rule) {
        return impurity(rule, size, count);
    }

    /**
     * Returns the impurity of samples.
     * @param size the number of samples.
     * @param count the number of samples in each class.
     * @return  the impurity of node
     */
    public static double impurity(SplitRule rule, int size, int[] count) {
        double impurity = 0.0;

        switch (rule) {
            case GINI:
                double squared_sum = 0;
                for (int c : count) {
                    if (c > 0) {
                        squared_sum += (double) c * c;
                    }
                }
                impurity = 1 - squared_sum / ((double) size * size);
                break;

            case ENTROPY:
                for (int c : count) {
                    if (c > 0) {
                        double p = (double) c / size;
                        impurity -= p * MathEx.log2(p);
                    }
                }
                break;

            case CLASSIFICATION_ERROR:
                impurity = Math.abs(1 - MathEx.max(count) / (double) size);
                break;
        }

        return impurity;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DecisionNode) {
            DecisionNode a = (DecisionNode) o;
            return output == a.output;
        }

        return false;
    }

    /** Returns the class probability. */
    public double[] posteriori(double[] prob) {
        return posteriori(count, prob);
    }

    /** Returns the class probability. */
    public static double[] posteriori(int[] count, double[] prob) {
        int k = count.length;
        double n = MathEx.sum(count) + k;
        for (int i = 0; i < k; i++) {
            prob[i] = (count[i] + 1) / n;
        }
        return prob;
    }

    /** Returns the deviance of node. */
    public static double deviance(int[] count, double[] prob) {
        int k = count.length;
        double d = 0.0;
        for (int i = 0; i < k; i++) {
            d -= count[i] * Math.log(prob[i]);
        }
        return 2 * d;
    }
}
