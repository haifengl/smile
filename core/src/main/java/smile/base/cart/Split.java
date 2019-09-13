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

import java.util.Comparator;
import java.util.function.IntPredicate;

/** The data about of a potential split for a leaf node. */
public abstract class Split {
    public static Comparator<Split> comparator = (x, y) -> Double.compare(x.score, y.score);

    /** The node associated with this split. */
    final LeafNode leaf;

    /** The parent node of the leaf to be split. */
    final InternalNode parent;

    /**
     * The split feature for this node.
     */
    final int feature;

    /**
     * Reduction in splitting criterion.
     */
    final double score;

    /**
     * The inclusive lower bound of the data partition in the reordered sample index array.
     */
    final int lo;

    /**
     * The exclusive upper bound of the data partition in the reordered sample index array.
     */
    final int hi;

    /**
     * True if all the samples in the split have the same value in the column.
     */
    final boolean[] pure;

    /** Constructor. */
    public Split(InternalNode parent, LeafNode leaf, int feature, double score, int lo, int hi, boolean[] pure) {
        this.parent = parent;
        this.leaf = leaf;
        this.feature = feature;
        this.score = score;
        this.lo = lo;
        this.hi = hi;
        this.pure =  pure;
    }

    /**
     * Returns an internal node with the feature, value, and score of this split.
     * @param trueChild the child node of true branch.
     * @param falseChild the child node of false branch.
     * @return an internal node
     */
    public abstract InternalNode toNode(Node trueChild, Node falseChild);

    /** Returns the lambda that tests on the split feature. */
    public abstract IntPredicate predicate();
}
