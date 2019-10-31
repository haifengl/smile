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

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/** The data about of a potential split for a leaf node. */
public abstract class Split {
    public static Comparator<Split> comparator = (x, y) -> Double.compare(x.score, y.score);

    /** The node associated with this split. */
    final LeafNode leaf;

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

    /** The number of samples in true branch. */
    final int trueCount;

    /** The number of samples in false branch. */
    final int falseCount;

    /** The parent node of the leaf to be split. */
    InternalNode parent;

    /**
     * unsplittable[j] is true if
     * <ul>
     * <li>The column j in the node is constant</li>
     * <li>The column j is almost constant, i.e. but after the split,
     * the size of one or two children is less than nodeSize.</li>
     * <li>The column j is not (almost) constant, but the impurity of
     * node doesn't decrease after the split. Therefore, the mutual
     * information between y and column j is very low.</li>
     * </ul>
     * For the first two cases, the column j in the children nodes
     * (split on another column) are still unsplittable. In last case,
     * column j may become splittable later after the node splits on
     * another column. However, we still would like to ignore it
     * because of the low mutual information. It likely introduces
     * more noise than information.
     */
    boolean[] unsplittable;

    /** The depth of node in the tree. */
    int depth = 1;

    /** Constructor. */
    public Split(LeafNode leaf, int feature, double score, int lo, int hi, int trueCount, int falseCount) {
        this.leaf = leaf;
        this.feature = feature;
        this.score = score;
        this.lo = lo;
        this.hi = hi;
        this.trueCount = trueCount;
        this.falseCount = falseCount;
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

    @Override
    public String toString() {
        String[] fields = {
                "feature: " + feature,
                "score: " + score,
                "lo: " + lo,
                "hi: " + hi,
                "true: " + trueCount,
                "false: " + falseCount,
                "depth: " + depth
        };

        return Arrays.stream(fields).collect(Collectors.joining(",\n", "{\n", "\n}"));
    }
}
