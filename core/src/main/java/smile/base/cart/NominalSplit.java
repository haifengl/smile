/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.base.cart;

import java.util.function.IntPredicate;

/**
 * The data about of a potential split for a leaf node.
 *
 * @author Haifeng Li
 */
public class NominalSplit extends Split {
    /**
     * The split value.
     */
    final int value;

    /**
     * The lambda returns true if the sample passes the test on the split feature.
     */
    final IntPredicate predicate;

    /**
     * Constructor.
     * @param leaf the node to split.
     * @param feature the index of feature column.
     * @param value the split value.
     * @param score the split score.
     * @param lo the lower bound of sample index in the node.
     * @param hi the upper bound of sample index in the node.
     * @param trueCount the number of samples in true branch child.
     * @param falseCount the number of samples false branch child.
     * @param predicate the lambda returns true if the sample passes the test on the split feature.
     */
    public NominalSplit(LeafNode leaf, int feature, int value, double score, int lo, int hi, int trueCount, int falseCount, IntPredicate predicate) {
        super(leaf, feature, score, lo, hi, trueCount, falseCount);
        this.value = value;
        this.predicate = predicate;
    }

    @Override
    public NominalNode toNode(Node trueChild, Node falseChild) {
        return new NominalNode(feature, value, score, leaf.deviance(), trueChild, falseChild);
    }

    @Override
    public IntPredicate predicate() {
        return predicate;
    }
}
