/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
public class Model extends Formula {
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
