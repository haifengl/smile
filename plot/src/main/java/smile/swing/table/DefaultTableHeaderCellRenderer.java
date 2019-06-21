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

package smile.swing.table;

import java.awt.Component;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * A default cell renderer for a JTableHeader.
 * <P>
 * DefaultTableHeaderCellRenderer attempts to provide identical behavior to the
 * renderer which the Swing subsystem uses by default, the Sun proprietary class
 * sun.swing.table.DefaultTableCellHeaderRenderer.
 * <P>
 * To apply any desired customization, DefaultTableHeaderCellRenderer may be
 * suitably extended.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DefaultTableHeaderCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructs a
     * <code>DefaultTableHeaderCellRenderer</code>.
     * <P>
     * The horizontal alignment and text position are set as appropriate to a
     * table header cell, and the opaque property is set to false.
     */
    public DefaultTableHeaderCellRenderer() {
        setHorizontalAlignment(CENTER);
        setHorizontalTextPosition(LEFT);
        setVerticalAlignment(BOTTOM);
        setOpaque(false);
    }

    /**
     * Returns the default table header cell renderer.
     * <P>
     * If the column is sorted, the approapriate icon is retrieved from the
     * current Look and Feel, and a border appropriate to a table header cell is
     * applied.
     * <P>
     * Subclasses may overide this method to provide custom content or
     * formatting.
     *
     * @param table the <code>JTable</code>.
     * @param value the value to assign to the header cell
     * @param isSelected This parameter is ignored.
     * @param hasFocus This parameter is ignored.
     * @param row This parameter is ignored.
     * @param column the column of the header cell to render
     * @return the default table header cell renderer
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value,
                isSelected, hasFocus, row, column);
        JTableHeader tableHeader = table.getTableHeader();
        if (tableHeader != null) {
            setForeground(tableHeader.getForeground());
        }
        setIcon(getIcon(table, column));
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        return this;
    }

    /**
     * Overloaded to return an icon suitable to the primary sorted column, or
     * null if the column is not the primary sort key.
     *
     * @param table the <code>JTable</code>.
     * @param column the column index.
     * @return the sort icon, or null if the column is unsorted.
     */
    protected Icon getIcon(JTable table, int column) {
        SortKey sortKey = getSortKey(table, column);
        if (sortKey != null && table.convertColumnIndexToView(sortKey.getColumn()) == column) {
            switch (sortKey.getSortOrder()) {
                case ASCENDING:
                    return UIManager.getIcon("Table.ascendingSortIcon");
                case DESCENDING:
                    return UIManager.getIcon("Table.descendingSortIcon");
                    
                default:
                    // Just to remove unmatched case warning
            }
        }
        return null;
    }

    /**
     * Returns the current sort key, or null if the column is unsorted.
     *
     * @param table the table
     * @param column the column index
     * @return the SortKey, or null if the column is unsorted
     */
    @SuppressWarnings("rawtypes")
    protected SortKey getSortKey(JTable table, int column) {
        RowSorter rowSorter = table.getRowSorter();
        if (rowSorter == null) {
            return null;
        }

        List sortedColumns = rowSorter.getSortKeys();
        if (!sortedColumns.isEmpty()) {
            return (SortKey) sortedColumns.get(0);
        }
        return null;
    }
}
