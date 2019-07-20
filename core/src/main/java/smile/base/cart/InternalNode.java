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

import smile.math.MathEx;

/**
 * An internal node in CART.
 */
public abstract class InternalNode extends Node {

    /**
     * Children node.
     */
    Node trueChild;

    /**
     * Children node.
     */
    Node falseChild;

    /** The name of split feature. */
    String featureName;

    /**
     * The split feature for this node.
     */
    int splitFeature = -1;

    /**
     * Reduction in squared error compared to parent.
     */
    double splitScore = 0.0;

    public InternalNode(int id, double output, String featureName, int splitFeature, double splitScore, Node trueChild, Node falseChild) {
        super(id, output);
        this.featureName = featureName;
        this.splitFeature = splitFeature;
        this.splitScore = splitScore;
        this.trueChild = trueChild;
        this.falseChild = falseChild;
    }

    @Override
    public int depth() {
        // compute the depth of each subtree
        int ld = trueChild.depth();
        int rd = falseChild.depth();

        // use the larger one
        return Math.max(ld, rd) + 1;
    }
}
