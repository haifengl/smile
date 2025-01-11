/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.util.Optional;
import java.util.stream.*;
import smile.data.measure.Measure;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * Abstract base class implementation of ValueVector interface.
 *
 * @author Haifeng Li
 */
public abstract class AbstractVector implements ValueVector {
    /** The struct field of the vector. */
    final StructField field;
    /** The index of the elements in the underlying data. */
    Index index;

    /**
     * Constructor.
     * @param field The struct field of the vector.
     */
    public AbstractVector(StructField field) {
        this(field, null);
    }

    /**
     * Constructor.
     * @param field The struct field of the vector.
     * @param index The index of the elements in the underlying data.
     */
    public AbstractVector(StructField field, Index index) {
        this.field = field;
        this.index = index;
    }

    @Override
    public String toString() {
        int limit = Math.min(size(), 10);
        String prefix = field.name() + "[";
        String suffix = size() > 10 ?  ", ...]" : "]";
        return IntStream.range(0, limit)
                .mapToObj(i -> field().toString(get(i)))
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    @Override
    public StructField field() {
        return field;
    }

    @Override
    public int size() {
        return Optional.ofNullable(index)
                .map(Index::size)
                .orElse(length());
    }

    /**
     * Returns the length of underlying data.
     * @return the length of underlying data.
     */
    abstract int length();

    /**
     * Check if a field's measure is invalid.
     * @param field a struct field.
     * @param invalidMeasure invalid measure class.
     * @return the struct field.
     */
    static StructField checkMeasure(StructField field, Class<? extends Measure> invalidMeasure) {
        if (invalidMeasure.isInstance(field.measure())) {
            throw new IllegalArgumentException(String.format("Invalid measure %s for %s", field.measure(), field.dtype()));
        }
        return field;
    }

    /**
     * Returns the index to the underlying data.
     * @param i the index to the vector element.
     * @return the index to the underlying data.
     */
    int at(int i) {
        return index == null ? i : index.apply(i);
    }

    /**
     * Returns the stream of index values.
     * @return the stream of index values.
     */
    IntStream indexStream() {
        return index == null ? IntStream.range(0, length()) : index.stream();
    }

    /**
     * Sets the index and null bitmap in the sliced vector.
     * @param vector the sliced vector.
     * @param index the slicing index.
     * @return the sliced vector.
     */
    <T extends AbstractVector> T slice(T vector, Index index) {
        if (this.index != null) {
            index = this.index.flatten(index);
        }

        vector.index = index;
        return vector;
    }
}
