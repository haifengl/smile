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

/**
 * A leaf node in decision tree.
 */
public class LeafNode extends Node {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param output the predicted value for this node.
     */
    public LeafNode(int id, double output) {
        super(id, output);
    }

    @Override
    public double predict(double[] x) {
        return output;
    }

    @Override
    public String toDot() {
        return String.format(" %d [label=<class = %d>, fillcolor=\"#00000000\", shape=ellipse];\n", id, output);
    }

    @Override
    public int depth() {
        return 1;
    }
}
