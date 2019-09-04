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

/**
 * A leaf node in decision tree.
 */
public interface LeafNode extends Node {
    /**
     * Splits this node. If success, return an internal
     * node with children leaf nodes. If not, return
     * this node itself.
     */
    Node split();

    @Override
    default LeafNode predict(Tuple x) {
        return this;
    }

    @Override
    default int depth() {
        return 1;
    }

    @Override
    default Node toLeaf() { return this; }
}
