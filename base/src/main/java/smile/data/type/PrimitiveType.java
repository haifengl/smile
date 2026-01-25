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
package smile.data.type;

/**
 * Primitive data type.
 *
 * @author Haifeng Li
 */
public abstract class PrimitiveType implements DataType {
    /** The type ID. */
    final ID id;
    /** True if the data may be null. */
    final boolean nullable;

    /**
     * Constructor.
     * @param id The type ID.
     * @param nullable True if the data may be null.
     */
    PrimitiveType(ID id, boolean nullable) {
        this.id = id;
        this.nullable = nullable;
    }

    @Override
    public ID id() {
        return id;
    }

    @Override
    public String name() {
        String name = id.name();
        return nullable ? name : name.toLowerCase();
    }

    @Override
    public String toString() {
        return id.name().toLowerCase();
    }

    @Override
    public boolean isNullable() {
        return nullable;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PrimitiveType t) {
            return id == t.id && nullable == t.nullable;
        }
        return false;
    }
}
