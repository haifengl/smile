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
