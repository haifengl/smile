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
import java.text.ParseException;

import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * Implements a cell editor that uses a formatted text field
 * to edit double[] values.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DoubleArrayCellEditor extends DefaultCellEditor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DoubleArrayCellEditor.class);

    final JFormattedTextField textField;

    /**
     * Constructor.
     */
    public DoubleArrayCellEditor() {
        super(new JFormattedTextField());
        textField = (JFormattedTextField) getComponent();
        
        DefaultFormatter formatter = new DefaultFormatter() {
            @Override
            public Object stringToValue(String string) throws ParseException {
                string = string.trim();
                if (string.isEmpty()) {
                    throw new ParseException("Empty string", 0);
                }

                int begin = 0;
                char ch = string.charAt(0);
                if (ch == '[' || ch == '{' || ch == '<') {
                    begin = 1;
                }

                int end = string.length();
                ch = string.charAt(end - 1);
                if (ch == ']' || ch == '}' || ch == '>') {
                    end -= 1;
                }

                string = string.substring(begin, end);
                String[] items = string.split("\\s*[ ,;:]\\s*");

                double[] data = new double[items.length];
                for (int i = 0; i < data.length; i++) {
                    data[i] = Double.parseDouble(items[i].trim());
                }

                return data;
            }

            @Override
            public String valueToString(Object value) throws ParseException {
                if (value == null) {
                    return "";
                }
                
                StringBuilder builder = new StringBuilder();

                if (value instanceof float[] data) {
                    if (data.length > 0) {
                        builder.append("[").append(data[0]);
                    }

                    for (int i = 1; i < data.length; i++) {
                        builder.append(", ").append(data[i]);
                    }
                    builder.append("]");

                } else if (value instanceof double[] data) {
                    if (data.length > 0) {
                        builder.append("[").append(data[0]);
                    }

                    for (int i = 1; i < data.length; i++) {
                        builder.append(", ").append(data[i]);
                    }
                    builder.append("]");
                } else {
                    throw new ParseException("Unsupport data type: " + value.getClass(), 0);
                }

                return builder.toString();
            }
        };
                
        formatter.setOverwriteMode(false);
        textField.setFormatterFactory(new DefaultFormatterFactory(formatter));
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
                        logger.debug("Failed to commit edit: ", ex);
                    }
                }
            }
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        textField.setValue(value);
        return textField;
    }

    @Override
    public Object getCellEditorValue() {
        JFormattedTextField ftf = (JFormattedTextField) getComponent();
        Object o = ftf.getValue();
        if (o instanceof double[]) {
            return o;
        } else {
            logger.debug("getCellEditorValue: can't parse o{}", o);
            return null;
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
                logger.debug("Failed to commit edit: ", ex);
            }

        } else { //text is invalid
            Toolkit.getDefaultToolkit().beep();
            textField.selectAll();
            return false; //don't let the editor go away
        }
        return super.stopCellEditing();
    }
}
