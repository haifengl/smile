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

import smile.data.type.StructType;

/**
 * A leaf node in decision tree.
 */
public class DecisionNode implements LeafNode {
    private static final long serialVersionUID = 1L;

    /** The predicted output. */
    private int output;

    /**
     * Constructor.
     *
     * @param output the predicted value for this node.
     */
    public DecisionNode(int output) {
        this.output = output;
    }

    /** Returns the predicted value. */
    public int output() {
        return output;
    }

    @Override
    public String toDot(StructType schema, int id) {
        return String.format(" %d [label=<class = %d>, fillcolor=\"#00000000\", shape=ellipse];\n", id, output);
    }
}
