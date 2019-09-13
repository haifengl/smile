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

import java.util.function.IntPredicate;
import smile.data.DataFrame;
import smile.data.vector.BaseVector;

/** The data about of a potential split for a leaf node. */
public class OrdinalSplit extends Split {
    /**
     * The split value.
     */
    final double value;

    /**
     * The lambda returns true if the sample passes the test on the split feature.
     */
    final IntPredicate predicate;

    /** Constructor. */
    public OrdinalSplit(final DataFrame df, InternalNode parent, LeafNode leaf, int feature, double value, double score, int lo, int hi, boolean[] pure) {
        super(parent, leaf, feature, score, lo, hi, pure);
        this.value = value;

        final BaseVector vector = df.vector(feature);
        predicate = (int o) -> vector.getDouble(o) <= value;
    }

    @Override
    public OrdinalNode toNode(Node trueChild, Node falseChild) {
        return new OrdinalNode(feature, value, score, trueChild, falseChild);
    }

    @Override
    public IntPredicate predicate() {
        return predicate;
    }
}
