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
 * A node with a nominal split variable.
 */
public class NominalNode extends InternalNode {
    private static final long serialVersionUID = 1L;

    /**
     * The split value.
     */
    int splitValue = -1;

    /** Constructor. */
    public NominalNode(int id, double output, String featureName, int splitFeature, int splitValue, double splitScore, Node trueChild, Node falseChild) {
        super(id, output, featureName, splitFeature, splitScore, trueChild, falseChild);
        this.splitValue = splitValue;
    }

    @Override
    public double predict(double[] x) {
        return x[splitFeature] == splitValue ? trueChild.predict(x) : falseChild.predict(x);
    }

    @Override
    public String toDot() {
        return String.format(" %d [label=<%s = %s<br/>nscore = %.4f>, fillcolor=\"#00000000\"];\n", id, featureName, splitValue, splitScore);
    }
}
