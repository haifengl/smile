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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.util.BitSet;
import java.util.stream.*;
import smile.data.type.StructField;

/**
 * Abstract base class implementation of ValueVector interface.
 *
 * @author Haifeng Li
 */
public abstract class AbstractVector implements ValueVector {
    /** The struct field of the vector. */
    final StructField field;

    /**
     * Constructor.
     * @param field The struct field of the vector.
     */
    public AbstractVector(StructField field) {
        this.field = field;
    }

    @Override
    public String toString() {
        int limit = Math.min(size(), 10);
        String prefix = field.name() + "[";
        String suffix = size() > 10 ?  String.format(", ..., %d more]", size()-10) : "]";
        return IntStream.range(0, limit)
                .mapToObj(i -> field().toString(get(i)))
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    @Override
    public StructField field() {
        return field;
    }

    /**
     * Returns the stream of indices.
     * @return the stream of indices.
     */
    IntStream index() {
        return IntStream.range(0, size());
    }

    /**
     * Converts a boolean array to BitSet.
     * @param vector a boolean array.
     * @return the BitSet.
     */
    static BitSet bitSet(boolean[] vector) {
        BitSet bits = new BitSet(vector.length);
        for (int i = 0; i < vector.length; i++) {
            if (vector[i]) {
                bits.set(i);
            }
        }
        return bits;
    }
}
