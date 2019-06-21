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

import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.JFormattedTextField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * Implements a cell editor that uses a formatted text field
 * to edit Double values.
 */
@SuppressWarnings("serial")
public class DoubleCellEditor extends DefaultCellEditor {
    private final static Logger LOGGER = Logger.getLogger(DoubleCellEditor.class.getName()); 
    private final static DoubleCellEditor instance = new DoubleCellEditor();

    JFormattedTextField textField;
    NumberFormat doubleFormat;
    private Double minimum, maximum;

    /**
     * Constructor.
     */
    public DoubleCellEditor() {
        this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
    
    /**
     * Constructor.
     * @param min the minimum of valid values.
     * @param max the maximum of valid values. 
     */
    public DoubleCellEditor(double min, double max) {
        super(new JFormattedTextField());
        textField = (JFormattedTextField) getComponent();
        minimum = new Double(min);
        maximum = new Double(max);

        // Set up the editor for the double cells.
        doubleFormat = NumberFormat.getNumberInstance();
        NumberFormatter doubleFormatter = new NumberFormatter(doubleFormat);
        doubleFormatter.setFormat(doubleFormat);
        doubleFormatter.setOverwriteMode(false);
        doubleFormatter.setMinimum(minimum);
        doubleFormatter.setMaximum(maximum);

        textField.setFormatterFactory(new DefaultFormatterFactory(doubleFormatter));
        textField.setValue(minimum);
        textField.setHorizontalAlignment(JTextField.TRAILING);
        textField.setFocusLostBehavior(JFormattedTextField.PERSIST);

        // React when the user presses Enter while the editor is
        // active.  (Tab is handled as specified by
        // JFormattedTextField's focusLostBehavior property.)
        textField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check");
        textField.getActionMap().put("check", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (!textField.isEditValid()) { //The text is invalid.
                    Toolkit.getDefaultToolkit().beep();
                    textField.selectAll();
                } else {
                    try {              //The text is valid,
                        textField.commitEdit();     //so use it.
                        textField.postActionEvent(); //stop editing
                    } catch (java.text.ParseException ex) {
                    }
                }
            }
        });
    }

    /**
     * Returns the default instance.
     */
    public static DoubleCellEditor getInstance() {
        return instance;
    }
    
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        JFormattedTextField ftf = (JFormattedTextField) super.getTableCellEditorComponent(table, value, isSelected, row, column);
        ftf.setValue(value);
        return ftf;
    }

    @Override
    public Object getCellEditorValue() {
        JFormattedTextField ftf = (JFormattedTextField) getComponent();
        Object o = ftf.getValue();
        if (o instanceof Double) {
            return o;
        } else if (o instanceof Number) {
            return new Double(((Number) o).doubleValue());
        } else {
            try {
                return doubleFormat.parseObject(o.toString());
            } catch (ParseException ex) {
                LOGGER.log(Level.FINE, "getCellEditorValue: can't parse {0}", o);
                return null;
            }
        }
    }

    // Override to check whether the edit is valid,
    // setting the value if it is and complaining if
    // it isn't.  If it's OK for the editor to go
    // away, we need to invoke the superclass's version 
    // of this method so that everything gets cleaned up.
    @Override
    public boolean stopCellEditing() {
        JFormattedTextField ftf = (JFormattedTextField) getComponent();
        if (ftf.isEditValid()) {
            try {
                ftf.commitEdit();
            } catch (java.text.ParseException ex) {
            }

        } else { //text is invalid
            Toolkit.getDefaultToolkit().beep();
            textField.selectAll();
            return false; //don't let the editor go away
        }
        return super.stopCellEditing();
    }
}
