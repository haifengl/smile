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
public abstract class PrimitiveVector extends AbstractVector {
    /**
     * Constructor.
     * @param field The struct field of the vector.
     */
    public PrimitiveVector(StructField field) {
        super(field);
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean isNullAt(int i) {
        return false;
    }

    @Override
    public int getNullCount() {
        return 0;
    }
}
