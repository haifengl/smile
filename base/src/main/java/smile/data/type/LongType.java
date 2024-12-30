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
package smile.data.type;

/**
 * Long data type.
 *
 * @author Haifeng Li
 */
public class LongType extends PrimitiveType {
    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    LongType(boolean nullable) {
        super(nullable);
    }

    @Override
    public boolean isLong() {
        return true;
    }

    @Override
    public ID id() {
        return ID.Long;
    }

    @Override
    public String name() {
        return nullable ? "Long" : "long";
    }

    @Override
    public String toString() {
        return "long";
    }

    @Override
    public Long valueOf(String s) {
        return Long.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LongType t) {
            return nullable == t.nullable;
        }
        return false;
    }
}
