/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/**
 * Color editor in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ColorCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {

    /**
     * Action command.
     */
    private static final String EDIT = "edit";
    /**
     * Current color.
     */
    private Color currentColor;
    /**
     * Editor component.
     */
    private JButton button;
    /**
     * Color chooser.
     */
    private JColorChooser colorChooser;
    /**
     * Color chooser dialog.
     */
    private JDialog dialog;

    /**
     * Constructor.
     */
    public ColorCellEditor() {
        button = new JButton() {
            @Override
            public void paintComponent(Graphics g) {
                // When the buttons are pressed they are redrawn with the default
                // background color but not what we want.
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        
        button.setActionCommand(EDIT);
        button.addActionListener(this);

        //Set up the dialog that the button brings up.
        colorChooser = new JColorChooser();
        dialog = JColorChooser.createDialog(button, "Pick a Color", true, colorChooser, this, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (EDIT.equals(cmd)) {
            // The user has clicked the cell, so bring up the dialog.
            button.setBackground(currentColor);
            dialog.setVisible(true);

            fireEditingStopped(); // Make the renderer reappear.

        } else { // User pressed dialog's "OK" button.
            currentColor = colorChooser.getColor();
        }
    }

    @Override
    public Object getCellEditorValue() {
        return currentColor;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentColor = (Color) value;
        return button;
    }
}
