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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * Implements a cell editor that uses a formatted text field
 * to edit Date values.
 */
@SuppressWarnings("serial")
public class DateCellEditor extends DefaultCellEditor {
    private final static Logger LOGGER = Logger.getLogger(DoubleCellEditor.class.getName()); 
    
    public static final DateCellEditor YYYYMMDD        = new DateCellEditor("yyyy-MM-dd");
    public static final DateCellEditor MMDDYY          = new DateCellEditor("MM/dd/yy");
    public static final DateCellEditor YYYYMMDD_HHMMSS = new DateCellEditor("yyyy-MM-dd HH:mm:ss");
    public static final DateCellEditor YYYYMMDD_HHMM   = new DateCellEditor("yyyy-MM-dd HH:mm");
    public static final DateCellEditor HHMM            = new DateCellEditor("HH:mm");
    public static final DateCellEditor HHMMSS          = new DateCellEditor("HH:mm:ss");
    public static final DateCellEditor ISO8601         = new DateCellEditor("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    JFormattedTextField textField;
    DateFormat dateFormat;

    /**
     * Constructor.
     */
    public DateCellEditor(String format) {
        this(new SimpleDateFormat(format));
    }
    
    /**
     * Constructor.
     */
    public DateCellEditor(DateFormat dateFormat) {
        super(new JFormattedTextField());
        textField = (JFormattedTextField) getComponent();

        this.dateFormat = dateFormat;
        DateFormatter dateFormatter = new DateFormatter(dateFormat);

        textField.setFormatterFactory(new DefaultFormatterFactory(dateFormatter));
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
        if (o instanceof Date) {
            return o;
        } else {
            try {
                return dateFormat.parseObject(o.toString());
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
