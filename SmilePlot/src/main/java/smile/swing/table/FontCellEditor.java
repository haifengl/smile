/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing.table;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import smile.swing.FontChooser;

/**
 * Font editor in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class FontCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {

    /**
     * Action command.
     */
    private static final String EDIT = "edit";
    /**
     * Current font.
     */
    private Font currentFont;
    /**
     * Editor component.
     */
    private JButton button;
    /**
     * Font chooser.
     */
    private FontChooser fontChooser;

    /**
     * Constructor.
     */
    public FontCellEditor() {
        button = new JButton("AaBbYyZz");
        button.setActionCommand(EDIT);
        button.addActionListener(this);

        // Set up the dialog that the button brings up.
        fontChooser = new FontChooser();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (EDIT.equals(cmd)) {
            // The user has clicked the cell, so bring up the dialog.
            button.setFont(currentFont);
            fontChooser.setSelectedFont(currentFont);
            if (fontChooser.showDialog(button) == FontChooser.OK_OPTION) {
                currentFont = fontChooser.getSelectedFont();                
            }

            fireEditingStopped(); // Make the renderer reappear.
        }
    }

    @Override
    public Object getCellEditorValue() {
        return currentFont;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentFont = (Font) value;
        return button;
    }
}
