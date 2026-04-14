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
package smile.swing.table;

import smile.data.DataFrame;

/**
 * A table model for data frames with paging.
 *
 * @author Haifeng Li
 */
public class DataFrameTableModel extends PageTableModel {
    /** The data frame. */
    final DataFrame df;

    /**
     * Constructor.
     * @param df the data frame.
     */
    public DataFrameTableModel(DataFrame df) {
        this.df = df;
    }

    @Override
    public int getColumnCount() {
        return df.ncol();
    }

    @Override
    public int getRealRowCount() {
        return df.nrow();
    }

    @Override
    public String getColumnName(int col) {
        return df.schema().names()[col];
    }

    @Override
    public Object getValueAtRealRow(int row, int col) {
        return df.get(row, col);
    }

    /**
     * Returns the column's Java type so that JTable can use the correct
     * renderer/sorter (e.g. numeric sort for number columns).
     */
    @Override
    public Class<?> getColumnClass(int col) {
        var type = df.schema().field(col).dtype();
        return switch (type.id()) {
            case Byte    -> java.lang.Byte.class;
            case Short   -> java.lang.Short.class;
            case Int     -> java.lang.Integer.class;
            case Long    -> java.lang.Long.class;
            case Float   -> java.lang.Float.class;
            case Double  -> java.lang.Double.class;
            case Boolean -> java.lang.Boolean.class;
            default      -> java.lang.String.class;
        };
    }
}
