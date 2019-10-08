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

import smile.data.Tuple;
import smile.regression.Regression;

/**
 * An internal node in CART.
 */
public abstract class InternalNode implements Node {

    /**
     * Children node.
     */
    Node trueChild;

    /**
     * Children node.
     */
    Node falseChild;

    /**
     * The split feature for this node.
     */
    int feature = -1;

    /**
     * Reduction in impurity compared to parent.
     */
    double score = 0.0;

    public InternalNode(int feature, double score, Node trueChild, Node falseChild) {
        this.feature = feature;
        this.score = score;
        this.trueChild = trueChild;
        this.falseChild = falseChild;
    }

    /**
     * Evaluate the tree over an instance.
     */
    public abstract LeafNode predict(Tuple x);

    @Override
    public int size() {
        return trueChild.size() + falseChild.size();
    }

    @Override
    public int depth() {
        // compute the depth of each subtree
        int ld = trueChild.depth();
        int rd = falseChild.depth();

        // use the larger one
        return Math.max(ld, rd) + 1;
    }

    @Override
    public Node merge() {
        trueChild = trueChild.merge();
        falseChild = falseChild.merge();

        if (trueChild instanceof DecisionNode && falseChild instanceof DecisionNode) {
            if (((DecisionNode) trueChild).output() == ((DecisionNode) falseChild).output()) {
                int[] a = ((DecisionNode) trueChild).count();
                int[] b = ((DecisionNode) falseChild).count();
                int[] count = new int[a.length];
                for (int i = 0; i < count.length; i++) {
                    count[i] = a[i] + b[i];
                }
                return new DecisionNode(count);
            }

        } else if (trueChild instanceof RegressionNode && falseChild instanceof RegressionNode) {
            if (((RegressionNode) trueChild).output() == ((RegressionNode) falseChild).output()) {
                RegressionNode a = (RegressionNode) trueChild;
                RegressionNode b = (RegressionNode) falseChild;

                int size = a.size + b.size;
                return new RegressionNode(size, a.output(), (a.size * a.mean() + b.size * b.mean()) / size, a.impurity() + b.impurity());
            }
        }

        return this;
    }
}
