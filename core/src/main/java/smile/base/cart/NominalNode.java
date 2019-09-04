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
import smile.data.measure.DiscreteMeasure;
import smile.data.type.StructType;
import smile.data.measure.Measure;

/**
 * A node with a nominal split variable.
 */
public class NominalNode extends InternalNode {
    private static final long serialVersionUID = 1L;

    /**
     * The split value.
     */
    int splitValue = Integer.MIN_VALUE;

    /** Constructor. */
    public NominalNode(int splitFeature, int splitValue, double splitScore, Node trueChild, Node falseChild) {
        super(splitFeature, splitScore, trueChild, falseChild);
        this.splitValue = splitValue;
    }

    @Override
    public LeafNode predict(Tuple x) {
        return x.getInt(splitFeature) == splitValue ? trueChild.predict(x) : falseChild.predict(x);
    }

    @Override
    public String toDot(StructType schema, int id) {
        String name = schema.fieldName(splitFeature);
        Measure measure = schema.measure(name);
        String value = (measure != null && measure instanceof DiscreteMeasure) ?
                ((DiscreteMeasure) measure).level(splitValue) :
                Integer.toString(splitValue);

        return String.format(" %d [label=<%s = %s<br/>nscore = %.4f>, fillcolor=\"#00000000\"];\n", id, name, value, splitScore);
    }
}
