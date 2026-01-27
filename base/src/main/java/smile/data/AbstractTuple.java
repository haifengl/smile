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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import smile.data.type.StructType;

/**
 * Abstract tuple base class.
 *
 * @author Haifeng Li
 */
public abstract class AbstractTuple implements Tuple {
    /** The schema of tuple. */
    protected final StructType schema;

    /**
     * Constructor.
     * @param schema the schema of tuple.
     */
    public AbstractTuple(StructType schema) {
        this.schema = schema;
    }

    @Override
    public StructType schema() {
        return schema;
    }

    @Override
    public String toString() {
        return schema.toString(this);
    }
}
