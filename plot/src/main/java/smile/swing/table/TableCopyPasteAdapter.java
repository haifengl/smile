/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.swing.table;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.StringTokenizer;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;

/**
 * TableCopyPasteAdapter enables Copy-Paste Clipboard functionality on
 * JTables. The clipboard data format used by the adapter is compatible with
 * the clipboard format used by Excel. This provides for clipboard
 * inter-operability between enabled JTables and Excel.
 *
 * @author Haifeng Li
 */
public class TableCopyPasteAdapter implements ActionListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TableCopyPasteAdapter.class);
    private final WeakReference<JTable> tableRef;

    /**
     * The copy/paste adapter is constructed with a JTable on which it enables
     * Copy-Paste and acts as a Clipboard listener.
     */
    private TableCopyPasteAdapter(JTable table) {
        this.tableRef = new WeakReference<>(table);
        KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false);
        // Identifying the copy KeyStroke user can modify this
        // to copy on some other Key combination.
        KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, false);
        // Identifying the Paste KeyStroke user can modify this
        //to copy on some other Key combination.
        table.registerKeyboardAction(this, "Copy", copy, JComponent.WHEN_FOCUSED);
        table.registerKeyboardAction(this, "Paste", paste, JComponent.WHEN_FOCUSED);
    }

    /**
     * Creates and attaches a copy-paste adapter for a table.
     * @param table the table.
     * @return an adapter.
     */
    public static TableCopyPasteAdapter apply(JTable table) {
        return new TableCopyPasteAdapter(table);
    }

    /**
     * This method is activated on the Keystrokes we are listening to in this
     * implementation. Here it listens for Copy and Paste ActionCommands.
     * Selections comprising non-adjacent cells result in invalid selection and
     * then copy action cannot be performed. Paste is done by aligning the upper
     * left corner of the selection with the 1st element in the current
     * selection of the JTable.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        var table = tableRef.get();
        if (table == null) return;
        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        if (e.getActionCommand().compareTo("Copy") == 0) {
            StringBuilder sb = new StringBuilder();
            // Check to ensure we have selected only a contiguous block of cells
            int numcols = table.getSelectedColumnCount();
            int numrows = table.getSelectedRowCount();
            int[] rowsselected = table.getSelectedRows();
            int[] colsselected = table.getSelectedColumns();
            if (!((numrows - 1 == rowsselected[rowsselected.length - 1] - rowsselected[0]
                    && numrows == rowsselected.length)
                    && (numcols - 1 == colsselected[colsselected.length - 1] - colsselected[0]
                    && numcols == colsselected.length))) {
                JOptionPane.showMessageDialog(null, "Invalid Copy Selection",
                        "Invalid Copy Selection", JOptionPane.ERROR_MESSAGE);
                return;
            }
            for (int i = 0; i < numrows; i++) {
                for (int j = 0; j < numcols; j++) {
                    sb.append(table.getValueAt(rowsselected[i], colsselected[j]));
                    if (j < numcols - 1) {
                        sb.append("\t");
                    }
                }
                sb.append("\n");
            }
            StringSelection stsel = new StringSelection(sb.toString());
            clipboard.setContents(stsel, stsel);
        }
        
        if (e.getActionCommand().compareTo("Paste") == 0) {
            int startRow = table.getSelectedRows()[0];
            int startCol = table.getSelectedColumns()[0];
            try {
                String trstring = (String) (clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor));
                StringTokenizer st1 = new StringTokenizer(trstring, "\n");
                for (int i = 0; st1.hasMoreTokens(); i++) {
                    String line = st1.nextToken();
                    StringTokenizer st2 = new StringTokenizer(line, "\t");
                    for (int j = 0; st2.hasMoreTokens(); j++) {
                        String token = st2.nextToken();
                        if (startRow + i < table.getRowCount() && startCol + j < table.getColumnCount()) {
                            table.setValueAt(token, startRow + i, startCol + j);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.debug("Failed to paste: ", ex);
            }
        }
    }
}
