/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.swing.table;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.UIManager;

import smile.swing.AlphaIcon;

/**
 * An extension of
 * <code>DefaultTableHeaderCellRenderer</code> that paints sort icons on the
 * header of each sorted column with varying opacity.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class MultiColumnSortTableHeaderCellRenderer extends DefaultTableHeaderCellRenderer {

    private float alpha;

    /**
     * Constructs a
     * <code>MultisortTableHeaderCellRenderer</code> with a default alpha of
     * 0.5.
     */
    public MultiColumnSortTableHeaderCellRenderer() {
        this(0.5F);
    }

    /**
     * Constructs a
     * <code>MultisortTableHeaderCellRenderer</code> with the specified alpha. A
     * lower value represents greater contrast between icons, while a higher
     * value can make more sort icons visible.
     *
     * @param alpha the opacity, in the range 0.0F to 1.0F. Recommended range:
     * 0.5F to 0.7F.
     */
    public MultiColumnSortTableHeaderCellRenderer(float alpha) {
        this.alpha = alpha;
    }

    /**
     * Overridden to return an icon suitable to a sorted column, or null if the
     * column is unsorted. The icon for the primary sorted column is fully
     * opaque, and the opacity is reduced by a factor of
     * <code>alpha</code> for each subsequent sort index.
     *
     * @param table the <code>JTable</code>.
     * @param column the column index.
     * @return the sort icon with appropriate opacity, or null if the column is
     * unsorted.
     */
    @Override
    public Icon getIcon(JTable table, int column) {
        float computedAlpha = 1.0F;
        for (RowSorter.SortKey sortKey : table.getRowSorter().getSortKeys()) {
            if (table.convertColumnIndexToView(sortKey.getColumn()) == column) {
                switch (sortKey.getSortOrder()) {
                    case ASCENDING:
                        return new AlphaIcon(UIManager.getIcon("Table.ascendingSortIcon"), computedAlpha);
                    case DESCENDING:
                        return new AlphaIcon(UIManager.getIcon("Table.descendingSortIcon"), computedAlpha);
                        
                    default:
                        // Just to remove unmatched case warning
                }
            }
            computedAlpha *= alpha;
        }
        return null;
    }
}
