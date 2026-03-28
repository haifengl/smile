/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model.cart;

import java.io.Serial;
import java.util.Arrays;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A node with a nominal split variable.
 *
 * @author Haifeng Li
 */
public class NominalNode extends InternalNode {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The split value.
     */
    final int value;

    /**
     * Constructor.
     * @param feature the index of feature column.
     * @param value the split value.
     * @param score the split score.
     * @param deviance the deviance.
     * @param trueChild the true branch child.
     * @param falseChild the false branch child.
     */
    public NominalNode(int feature, int value, double score, double deviance, Node trueChild, Node falseChild) {
        super(feature, score, deviance, trueChild, falseChild);
        this.value = value;
    }

    @Override
    public LeafNode predict(Tuple x) {
        return x.getInt(feature) == value ? trueChild.predict(x) : falseChild.predict(x);
    }

    @Override
    public boolean branch(Tuple x) {
        return x.getInt(feature) == value;
    }

    @Override
    public NominalNode replace(Node trueChild, Node falseChild) {
        return new NominalNode(feature, value, score, deviance, trueChild, falseChild);
    }

    @Override
    public String dot(StructType schema, StructField response, int id) {
        StructField field = schema.field(feature);
        return String.format(" %d [label=<%s = %s<br/>size = %d<br/>impurity reduction = %.4f>, fillcolor=\"#00000000\"];\n", id, field.name(), field.toString(value), size(), score);
    }

    @Override
    public String toString(StructType schema, boolean trueBranch) {
        StructField field = schema.field(feature);
        String values;
        if (trueBranch) {
            values = field.toString(value);
        } else {
            if (field.measure() instanceof NominalScale scale) {
                values = Arrays.stream(scale.values()).filter(v -> v != value).mapToObj(scale::level).collect(Collectors.joining(","));
            } else {
                values = "/=" + value;
            }
        }

        return String.format("%s=%s", field.name(), values);
    }
}
