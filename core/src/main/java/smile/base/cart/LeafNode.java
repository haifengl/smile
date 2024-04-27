/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.base.cart;

import smile.data.Tuple;

/**
 * A leaf node in decision tree.
 *
 * @author Haifeng Li
 */
public abstract class LeafNode implements Node {
    /** The number of samples in the node. */
    protected final int size;

    /**
     * Constructor.
     * @param size the number of samples in the node
     */
    public LeafNode(int size) {
        this.size = size;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int leaves() {
        return 1;
    }

    @Override
    public LeafNode predict(Tuple x) {
        return this;
    }

    @Override
    public int depth() {
        return 1;
    }

    @Override
    public Node merge() { return this; }
}
