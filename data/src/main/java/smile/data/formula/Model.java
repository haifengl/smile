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

package smile.data.formula;

import smile.data.Instance;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.StructType;

/**
 * A model specifies the predictors and the response.
 * Given a context (e.g. DataFrame), the formula can be used to
 * generate the model data.
 *
 * @author Haifeng Li
 */
class Model extends Formula {
    /** The response variable. */
    private Term y;

    /**
     * Constructor.
     * @param y the response variable. All other columns will be used as predictors.
     */
    public Model(Term y) {
        this(y, Terms.all());
    }

    /**
     * Constructor.
     * @param y the response variable.
     * @param x the predictor terms.
     */
    public Model(Term y, HyperTerm... x) {
        super(removeY(y, x));
        this.y = y;

        DataType type = y.type();
        if (!type.isPrimitive()) {
            throw new IllegalArgumentException(String.format("Invalid data type of y: %s", type));
        }
    }

    /** Returns a new array of terms that includes x but removes y. */
    private static HyperTerm[] removeY(Term y, HyperTerm... x) {
        HyperTerm[] terms = new HyperTerm[x.length+1];
        System.arraycopy(x, 0, terms, 0, x.length);
        terms[x.length] = new Delete(y);
        return terms;
    }

    @Override
    public StructType bind(StructType inputSchema) {
        y.bind(inputSchema);
        return super.bind(inputSchema);
    }

    /**
     * Apply the formula on a tuple to generate the model data.
     */
    public Instance<Tuple> map(Tuple o) {
        switch (y.type().id()) {
            case Double:
            case Float:
                return new Instance<Tuple>() {
                    @Override
                    public Tuple x() {
                        return apply(o);
                    }

                    @Override
                    public double y() {
                        return y.applyAsDouble(o);
                    }
                };

            case Integer:
            case Boolean:
            case Short:
            case Byte:
            case Char:
                return new Instance<Tuple>() {
                    @Override
                    public Tuple x() {
                        return apply(o);
                    }

                    @Override
                    public int label() {
                        return y.applyAsInt(o);
                    }
                };

            default:
                throw new UnsupportedOperationException("Unsupported response type: " + y.type());
        }
    }

    /**
     * Returns the response variable of a sample.
     */
    public double y(Tuple o) {
        return y.applyAsDouble(o);
    }

    /**
     * Returns the class label of a sample.
     */
    public int label(Tuple o) {
        return y.applyAsInt(o);
    }
}
