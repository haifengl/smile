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

import java.util.prefs.Preferences;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author Haifeng Li
 */
public class TableColumnSettings implements TableColumnModelListener {

    /**
     * The id of table to save and restore column settings (width, order,
     * visible).
     */
    private String id;
    /**
     * The table to apply.
     */
    private JTable table;
    /**
     * Preferences store.
     */
    private Preferences prefs = Preferences.userNodeForPackage(TableColumnSettings.class);

    /**
     * Constructor.
     *
     * @param id The id of table to save and restore column settings (width,
     * order, visible).
     */
    public TableColumnSettings(String id) {
        this.id = id;
    }

    /**
     * Apply this column settings to given table. This object will also listen
     * to table column model events.
     * @param table A JTable to apply column settings.
     */
    public void apply(JTable table) {
        this.table = table;
        restoreSettings();
        table.getColumnModel().addColumnModelListener(this);
    }

    private void restoreSettings() {
        TableColumnModel columnModel = table.getColumnModel();
        
        // restore column width
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn col = columnModel.getColumn(i);
            int idx = col.getModelIndex();
            int width = prefs.getInt(id + "-column-width-" + idx, 0);
            if (width != 0) {
                col.setPreferredWidth(width);
            }
        }

        // restore column order
        TableColumn column[] = new TableColumn[columnModel.getColumnCount()];

        for (int i = 0; i < column.length; i++) {
            column[i] = columnModel.getColumn(i);
        }

        // remove all columns
        while (columnModel.getColumnCount() > 0) {
            columnModel.removeColumn(columnModel.getColumn(0));
        }

        // add them back with saved order
        int visibleColumnCount = prefs.getInt(id + "-visible-column-count", column.length);
        for (int i = 0; i < visibleColumnCount; i++) {
            int idx = prefs.getInt(id + "-column-order-" + i, i);
            columnModel.addColumn(column[idx]);
        }
    }

    private void saveSettings() {
        TableColumnModel columnModel = table.getColumnModel();
        prefs.putInt(id + "-visible-column-count", columnModel.getColumnCount());
        
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn col = columnModel.getColumn(i);

            int idx = col.getModelIndex();
            int width = col.getWidth();
            prefs.putInt(id + "-column-width-" + idx, width);
            prefs.putInt(id + "-column-order-" + i, idx);
        }
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        saveSettings();
    }

    @Override
    public void columnAdded(TableColumnModelEvent tcme) {
        //saveSettings();
    }

    @Override
    public void columnRemoved(TableColumnModelEvent tcme) {
        //saveSettings();
    }

    @Override
    public void columnMoved(TableColumnModelEvent tcme) {
        saveSettings();
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent lse) {
    }
}
