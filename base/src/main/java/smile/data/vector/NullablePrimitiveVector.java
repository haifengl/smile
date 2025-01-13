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

import java.util.BitSet;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * Abstract base class implementation of ValueVector interface.
 *
 * @author Haifeng Li
 */
public abstract class NullablePrimitiveVector extends AbstractVector {
    /** The null bitmap. The bit is 1 if the value is null. */
    BitSet nullMask;

    /**
     * Constructor.
     * @param field The struct field of the vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullablePrimitiveVector(StructField field, BitSet nullMask) {
        super(field);
        this.nullMask = nullMask;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public boolean isNullAt(int i) {
        return nullMask.get(i);
    }

    @Override
    public int getNullCount() {
        return nullMask.cardinality();
    }

    /**
     * Sets the null bitmap mask.
     * @param mask The null bitmap mask.
     */
    public void setNullMask(BitSet mask) {
        nullMask = mask;
    }

    /**
     * Returns the null bitmap mask.
     * @return the null bitmap mask.
     */
    public BitSet getNullMask() {
        return nullMask;
    }
}
