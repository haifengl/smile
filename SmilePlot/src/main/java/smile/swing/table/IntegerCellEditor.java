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
 * to edit Integer values.
 */
@SuppressWarnings("serial")
public class IntegerCellEditor extends DefaultCellEditor {
    private final static Logger LOGGER = Logger.getLogger(IntegerCellEditor.class.getName()); 

    JFormattedTextField textField;
    NumberFormat integerFormat;
    private Integer minimum, maximum;

    /**
     * Constructor.
     */
    public IntegerCellEditor() {
        this(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    /**
     * Constructor.
     * @param min the minimum of valid values.
     * @param max the maximum of valid values. 
     */
    public IntegerCellEditor(int min, int max) {
        super(new JFormattedTextField());
        textField = (JFormattedTextField) getComponent();
        minimum = new Integer(min);
        maximum = new Integer(max);

        // Set up the editor for the integer cells.
        integerFormat = NumberFormat.getIntegerInstance();
        NumberFormatter intFormatter = new NumberFormatter(integerFormat);
        intFormatter.setFormat(integerFormat);
        intFormatter.setOverwriteMode(false);
        intFormatter.setMinimum(minimum);
        intFormatter.setMaximum(maximum);

        textField.setFormatterFactory(new DefaultFormatterFactory(intFormatter));
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
        if (o instanceof Integer) {
            return o;
        } else if (o instanceof Number) {
            return new Integer(((Number) o).intValue());
        } else {
            try {
                return integerFormat.parseObject(o.toString());
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
