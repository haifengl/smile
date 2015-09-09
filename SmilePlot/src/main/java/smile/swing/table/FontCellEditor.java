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
