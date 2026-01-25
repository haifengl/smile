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
package smile.data;

import smile.data.type.StructType;

/**
 * A row in data frame.
 * @param df the data frame that the row belongs to.
 * @param index the row index.
 */
public record Row(DataFrame df, int index) implements Tuple {
    @Override
    public String toString() {
        return schema().toString(this);
    }

    @Override
    public StructType schema() {
        return df.schema();
    }

    @Override
    public boolean isNullAt(int j) {
        return df.isNullAt(index, j);
    }

    @Override
    public boolean isNullAt(String field) {
        return df.isNullAt(index, indexOf(field));
    }

    @Override
    public Object get(int j) {
        return df.get(index, j);
    }

    @Override
    public boolean getBoolean(int j) {
        return df.column(j).getBoolean(index);
    }

    @Override
    public char getChar(int j) {
        return df.column(j).getChar(index);
    }

    @Override
    public byte getByte(int j) {
        return df.column(j).getByte(index);
    }

    @Override
    public short getShort(int j) {
        return df.column(j).getShort(index);
    }

    @Override
    public int getInt(int j) {
        return df.column(j).getInt(index);
    }

    @Override
    public long getLong(int j) {
        return df.column(j).getLong(index);
    }

    @Override
    public float getFloat(int j) {
        return df.column(j).getFloat(index);
    }

    @Override
    public double getDouble(int j) {
        return df.column(j).getDouble(index);
    }
}
