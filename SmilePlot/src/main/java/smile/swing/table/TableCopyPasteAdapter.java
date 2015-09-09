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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private final static Logger LOGGER = Logger.getLogger(TableCopyPasteAdapter.class.getName()); 

    private String rowstring, value;
    private Clipboard system;
    private StringSelection stsel;
    private JTable table;

    /**
     * The copy/paste adapter is constructed with a JTable on which it enables
     * Copy-Paste and acts as a Clipboard listener.
     */
    private TableCopyPasteAdapter(JTable table) {
        this.table = table;
        KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK, false);
        // Identifying the copy KeyStroke user can modify this
        // to copy on some other Key combination.
        KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK, false);
        // Identifying the Paste KeyStroke user can modify this
        //to copy on some other Key combination.
        table.registerKeyboardAction(this, "Copy", copy, JComponent.WHEN_FOCUSED);
        table.registerKeyboardAction(this, "Paste", paste, JComponent.WHEN_FOCUSED);
        system = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    public static TableCopyPasteAdapter apply(JTable table) {
        TableCopyPasteAdapter adapter = new TableCopyPasteAdapter(table);
        return adapter;
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
        if (e.getActionCommand().compareTo("Copy") == 0) {
            StringBuilder sbf = new StringBuilder();
            // Check to ensure we have selected only a contiguous block of
            // cells
            int numcols = table.getSelectedColumnCount();
            int numrows = table.getSelectedRowCount();
            int[] rowsselected = table.getSelectedRows();
            int[] colsselected = table.getSelectedColumns();
            if (!((numrows - 1 == rowsselected[rowsselected.length - 1] - rowsselected[0]
                    && numrows == rowsselected.length)
                    && (numcols - 1 == colsselected[colsselected.length - 1] - colsselected[0]
                    && numcols == colsselected.length))) {
                JOptionPane.showMessageDialog(null, "Invalid Copy Selection",
                        "Invalid Copy Selection",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            for (int i = 0; i < numrows; i++) {
                for (int j = 0; j < numcols; j++) {
                    sbf.append(table.getValueAt(rowsselected[i], colsselected[j]));
                    if (j < numcols - 1) {
                        sbf.append("\t");
                    }
                }
                sbf.append("\n");
            }
            stsel = new StringSelection(sbf.toString());
            system = Toolkit.getDefaultToolkit().getSystemClipboard();
            system.setContents(stsel, stsel);
        }
        
        if (e.getActionCommand().compareTo("Paste") == 0) {
            LOGGER.log(Level.FINE, "Trying to Paste");
            int startRow = (table.getSelectedRows())[0];
            int startCol = (table.getSelectedColumns())[0];
            try {
                String trstring = (String) (system.getContents(this).getTransferData(DataFlavor.stringFlavor));
                StringTokenizer st1 = new StringTokenizer(trstring, "\n");
                for (int i = 0; st1.hasMoreTokens(); i++) {
                    rowstring = st1.nextToken();
                    StringTokenizer st2 = new StringTokenizer(rowstring, "\t");
                    for (int j = 0; st2.hasMoreTokens(); j++) {
                        value = (String) st2.nextToken();
                        if (startRow + i < table.getRowCount()
                                && startCol + j < table.getColumnCount()) {
                            table.setValueAt(value, startRow + i, startCol + j);
                        }
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
}
