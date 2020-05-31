/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.data.formula;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * The flag if intercept should be included in the model.
 *
 * @author Haifeng Li
 */
final class Intercept implements Term {
    /** The flag if the model has the intercept. */
    private final boolean flag;

    /**
     * Constructor.
     *
     * @param flag The flag if the model has the intercept.
     */
    public Intercept(boolean flag) {
        this.flag = flag;
    }

    /** Returns the flag if the intercept is included in the model. */
    public boolean isInclulded() {
        return flag;
    }

    @Override
    public String name() {
        return flag ? "1" : "0";
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof Intercept) {
            return flag == ((Intercept) o).flag;
        }

        return false;
    }

    @Override
    public Set<String> variables() {
        return Collections.emptySet();
    }

    @Override
    public Object apply(Tuple o) {
        return flag ? 1 : 0;
    }

    @Override
    public int applyAsInt(Tuple o) {
        return flag ? 1 : 0;
    }

    @Override
    public long applyAsLong(Tuple o) {
        return flag ? 1 : 0;
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return flag ? 1 : 0;
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return flag ? 1 : 0;
    }

    @Override
    public DataType type() {
        return DataTypes.DoubleType;
    }

    @Override
    public Optional<Measure> measure() {
        return Optional.empty();
    }

    @Override
    public void bind(StructType schema) {
    }
}
