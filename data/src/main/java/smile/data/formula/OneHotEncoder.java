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

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

import java.util.Collections;
import java.util.Set;

/** The one-hot term. */
class OneHotEncoder extends AbstractTerm {
    /** The name of variable. */
    final String name;
    /** The column index of variable. */
    final int column;
    /** The integer value of level. */
    final int value;
    /** The level of nominal scale. */
    final String level;

    /**
     * Constructor.
     * @param name the name of variable.
     * @param column the column index of variable.
     * @param value the integer value of level.
     * @param level the level of nominal scale.
     */
    public OneHotEncoder(String name, int column, int value, String level) {
        this.name = name;
        this.column = column;
        this.value = value;
        this.level = level;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public Object apply(Tuple o) {
        return applyAsByte(o);
    }

    @Override
    public byte applyAsByte(Tuple o) {
        return value == ((Number) o.get(column)).intValue() ? (byte) 1 : (byte) 0;
    }

    @Override
    public String name() {
        return String.format("%s_%s", name, level);
    }

    @Override
    public DataType type() {
        return DataTypes.ByteType;
    }

    @Override
    public void bind(StructType schema) {

    }
}