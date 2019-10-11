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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/** The interaction of one-hot term. */
class OneHotEncoderInteraction extends AbstractTerm {
    /** The one-hot encoders. */
    List<OneHotEncoder> encoders;

    /**
     * Constructor.
     */
    public OneHotEncoderInteraction(List<OneHotEncoder> encoders) {
        this.encoders = encoders;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Set<String> variables() {
        Set<String> set = new HashSet<>();
        for (OneHotEncoder encoder : encoders) {
            set.addAll(encoder.variables());
        }
        return set;
    }

    @Override
    public Object apply(Tuple o) {
        return applyAsByte(o);
    }

    @Override
    public byte applyAsByte(Tuple o) {
        for (OneHotEncoder encoder : encoders) {
            if (encoder.applyAsByte(o) == 0) return 0;
        }
        return 1;
    }

    @Override
    public String name() {
        return encoders.stream()
                .map(encoder -> encoder.name())
                .collect(Collectors.joining("-"));
    }

    @Override
    public DataType type() {
        return DataTypes.ByteType;
    }

    @Override
    public void bind(StructType schema) {

    }
}