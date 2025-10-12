/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * Implements a cell editor that uses a formatted text field
 * to edit Date values.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DateCellEditor extends DefaultCellEditor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DateCellEditor.class);

    /** Predefined date editor in YYYY-MM-DD format. */
    public static final DateCellEditor YYYYMMDD        = new DateCellEditor("yyyy-MM-dd");
    /** Predefined date editor in MM/dd/yy format. */
    public static final DateCellEditor MMDDYY          = new DateCellEditor("MM/dd/yy");
    /** Predefined datetime editor in yyyy-MM-dd HH:mm:ss format. */
    public static final DateCellEditor YYYYMMDD_HHMMSS = new DateCellEditor("yyyy-MM-dd HH:mm:ss");
    /** Predefined datetime editor in yyyy-MM-dd HH:mm format. */
    public static final DateCellEditor YYYYMMDD_HHMM   = new DateCellEditor("yyyy-MM-dd HH:mm");
    /** Predefined time editor in HH:mm format. */
    public static final DateCellEditor HHMM            = new DateCellEditor("HH:mm");
    /** Predefined time editor in HH:mm:ss format. */
    public static final DateCellEditor HHMMSS          = new DateCellEditor("HH:mm:ss");
    /** Predefined date editor in ISO-8601 format yyyy-MM-dd'T'HH:mm:ss.SSSXXX. */
    public static final DateCellEditor ISO8601         = new DateCellEditor("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    /** The text field. */
    private final JFormattedTextField textField;
    /** The date format. */
    private final DateFormat dateFormat;

    /**
     * Constructor.
     * @param format the date regex format.
     */
    public DateCellEditor(String format) {
        this(new SimpleDateFormat(format));
    }
    
    /**
     * Constructor.
     * @param format the date format.
     */
    public DateCellEditor(DateFormat format) {
        super(new JFormattedTextField());
        textField = (JFormattedTextField) getComponent();

        this.dateFormat = format;
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
                        logger.debug("Failed to commit edit: ", ex);
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
                logger.debug("getCellEditorValue: can't parse {}", o);
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
