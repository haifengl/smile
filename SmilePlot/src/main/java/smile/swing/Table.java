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
package smile.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.HighlighterFactory;

import smile.swing.table.ByteArrayCellRenderer;
import smile.swing.table.ColorCellEditor;
import smile.swing.table.ColorCellRenderer;
import smile.swing.table.DateCellEditor;
import smile.swing.table.DateCellRenderer;
import smile.swing.table.DoubleArrayCellEditor;
import smile.swing.table.DoubleArrayCellRenderer;
import smile.swing.table.DoubleCellEditor;
import smile.swing.table.FloatArrayCellRenderer;
import smile.swing.table.FontCellEditor;
import smile.swing.table.FontCellRenderer;
import smile.swing.table.IntegerArrayCellEditor;
import smile.swing.table.IntegerArrayCellRenderer;
import smile.swing.table.IntegerCellEditor;
import smile.swing.table.LongArrayCellRenderer;
import smile.swing.table.MultiColumnSortTableHeaderCellRenderer;
import smile.swing.table.PageTableModel;
import smile.swing.table.ShortArrayCellRenderer;
import smile.swing.table.TableCopyPasteAdapter;

/**
 * Customized JTable with optional row number header. It also provides the
 * renderer and editor Color and Font. It also provides a renderer for
 * Float/Double of special values (such as NaN and Infinity).
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Table extends JXTable {

    /**
     * Color renderer.
     */
    private static TableCellRenderer colorRenderer = new ColorCellRenderer();
    /**
     * Font renderer.
     */
    private static TableCellRenderer fontRenderer = new FontCellRenderer();
    /**
     * Byte array renderer.
     */
    private static TableCellRenderer byteArrayRenderer = new ByteArrayCellRenderer();
    /**
     * Short array renderer.
     */
    private static TableCellRenderer shortArrayRenderer = new ShortArrayCellRenderer();
    /**
     * Integer array renderer.
     */
    private static TableCellRenderer intArrayRenderer = new IntegerArrayCellRenderer();
    /**
     * Long array renderer.
     */
    private static TableCellRenderer longArrayRenderer = new LongArrayCellRenderer();
    /**
     * Float array renderer.
     */
    private static TableCellRenderer floatArrayRenderer = new FloatArrayCellRenderer();
    /**
     * Double array renderer.
     */
    private static TableCellRenderer doubleArrayRenderer = new DoubleArrayCellRenderer();
    /**
     * Color editor.
     */
    private static TableCellEditor colorEditor = new ColorCellEditor();
    /**
     * Font editor.
     */
    private static TableCellEditor fontEditor = new FontCellEditor();
    /**
     * Integer editor.
     */
    private static TableCellEditor byteEditor = new IntegerCellEditor(-128, 127);
    /**
     * Integer editor.
     */
    private static TableCellEditor shortEditor = new IntegerCellEditor(-32768, 32767);
    /**
     * Integer editor.
     */
    private static TableCellEditor intEditor = new IntegerCellEditor(-2147483648, 2147483647);
    /**
     * Integer editor.
     */
    private static TableCellEditor floatEditor = new DoubleCellEditor(-Float.MAX_VALUE, Float.MAX_VALUE);
    /**
     * Double editor.
     */
    private static TableCellEditor doubleEditor = new DoubleCellEditor();
    /**
     * Integer array editor.
     */
    private static TableCellEditor intArrayEditor = new IntegerArrayCellEditor();
    /**
     * Double array editor.
     */
    private static TableCellEditor doubleArrayEditor = new DoubleArrayCellEditor();
    /**
     * Row header.
     */
    private RowHeader rowHeader;

    /**
     * Constructs a default JTable that is initialized with a default data
     * model, a default column model, and a default selection model. 
     */
    public Table() {
        init();
    }

    /**
     * Constructs a JTable with numRows and numColumns of empty cells using
     * DefaultTableModel. 
     * @param numRows the number of rows the table holds.
     * @param numColumns the number of columns the table holds.
     */
    public Table(int numRows, int numColumns) {
        super(numRows, numColumns);
        init();
    }

    /**
     * Constructs a JTable to display the values in the two dimensional array,
     * rowData, with column names, columnNames. 
     * @param rowData the data for the new table.
     * @param columnNames the names of each column.
     */
    public Table(Object[][] rowData, Object[] columnNames) {
        super(rowData, columnNames);
        init();
    }

    /**
     * Constructs a JTable that is initialized with dm as the data model,
     * a default column model, and a default selection model. 
     * @param dm the data model for the table
     */
    public Table(TableModel dm) {
        super(dm);
        init();
    }

    /**
     * Constructs a JTable that is initialized with dm as the data model,
     * cm as the column model, and a default selection model. 
     * @param dm the data model for the table.
     * @param cm the column model for the table.
     */
    public Table(TableModel dm, TableColumnModel cm) {
        super(dm, cm);
        init();
    }

    /**
     * Constructs a JTable that is initialized with dm as the data model,
     * cm as the column model, and sm as the selection model
     * @param dm the data model for the table.
     * @param cm the column model for the table.
     * @param sm the row selection model for the table.
     */
    public Table(TableModel dm, TableColumnModel cm, ListSelectionModel sm) {
        super(dm, cm, sm);
        init();
    }

    private void init() {
        setAutoResizeMode(AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);
        setColumnControlVisible(true);
        setHighlighters(HighlighterFactory.createAlternateStriping());
        setSortOrderCycle(SortOrder.ASCENDING, SortOrder.DESCENDING, SortOrder.UNSORTED);
        TableCopyPasteAdapter.apply(this);
        getTableHeader().setDefaultRenderer(new MultiColumnSortTableHeaderCellRenderer());
        // workaround with table row filter to let it register to table changes
        firePropertyChange("model", getModel(), getModel());
    }
    
    @Override
    public boolean getScrollableTracksViewportWidth() {
        // use all available horizontal space
        return getPreferredSize().width < getParent().getWidth();
    }
    
    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        Object obj = getValueAt(row, column);
        if (obj == null) {
            return super.getCellRenderer(row, column);
        }
        
        Class<?> clazz = obj.getClass();
        if (clazz.equals(Color.class)) {
            return colorRenderer;
        } else if (clazz.equals(Font.class)) {
            return fontRenderer;
        } else if (clazz.equals(java.sql.Date.class)) {
            return DateCellRenderer.YYYYMMDD;
        } else if (clazz.equals(java.sql.Time.class)) {
            return DateCellRenderer.HHMMSS;
        } else if (clazz.equals(java.sql.Timestamp.class)) {
            return DateCellRenderer.ISO8601;
        } else if (clazz.equals(byte[].class)) {
            return byteArrayRenderer;
        } else if (clazz.equals(short[].class)) {
            return shortArrayRenderer;
        } else if (clazz.equals(int[].class)) {
            return intArrayRenderer;
        } else if (clazz.equals(long[].class)) {
            return longArrayRenderer;
        } else if (clazz.equals(float[].class)) {
            return floatArrayRenderer;
        } else if (clazz.equals(double[].class)) {
            return doubleArrayRenderer;
        } else {
            return super.getCellRenderer(row, column);
        }
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
        Object obj = getValueAt(row, column);
        if (obj == null) {
            return super.getCellEditor(row, column);
        }
        
        Class<?> clazz = obj.getClass();

        if (clazz.equals(Color.class)) {
            return colorEditor;
        } else if (clazz.equals(Font.class)) {
            return fontEditor;
        } else if (clazz.equals(java.sql.Date.class)) {
            return DateCellEditor.YYYYMMDD;
        } else if (clazz.equals(java.sql.Time.class)) {
            return DateCellEditor.HHMMSS;
        } else if (clazz.equals(java.sql.Timestamp.class)) {
            return DateCellEditor.ISO8601;
        } else if (clazz.equals(java.util.Date.class)) {
            return DateCellEditor.YYYYMMDD_HHMMSS;
        } else if (clazz.equals(Byte.TYPE)) {
            return byteEditor;
        } else if (clazz.equals(Short.TYPE)) {
            return shortEditor;
        } else if (clazz.equals(Integer.TYPE)) {
            return intEditor;
        } else if (clazz.equals(Float.TYPE)) {
            return floatEditor;
        } else if (clazz.equals(Double.TYPE)) {
            return doubleEditor;
        } else if (clazz.equals(int[].class)) {
            return intArrayEditor;
        } else if (clazz.equals(double[].class)) {
            return doubleArrayEditor;
        } else {
            return super.getCellEditor(row, column);
        }
    }

    /**
     * Returns a row header for this table. The row header must be added to
     * the row header of the JScrollPane that contains this table.
     * @return the row header component.
     */
    public JTable getRowHeader() {
        if (rowHeader == null) {
            rowHeader = new RowHeader();
        }
        
        return rowHeader;
    }
    
    /**
     * Use a JTable as a renderer for row numbers of the main table.
     * This table must be added to the row header of the JScrollPane that
     * contains the main table.
     */
    public class RowHeader extends JXTable implements ChangeListener, PropertyChangeListener {

        /**
         * Constructor.
         */
        public RowHeader() {
            Table.this.addPropertyChangeListener(this);
            
            // row header should not allow to sort.
            setSortable(false);
            
            setFocusable(false);
            setAutoCreateColumnsFromModel(false);
            setModel(Table.this.getModel());
            setSelectionModel(Table.this.getSelectionModel());

            TableColumn column = new TableColumn();
            column.setHeaderValue(" ");
            addColumn(column);
            column.setCellRenderer(new RowNumberRenderer());

            getColumnModel().getColumn(0).setPreferredWidth(50);
            setPreferredScrollableViewportSize(getPreferredSize());
        }

        @Override
        public void addNotify() {
            super.addNotify();

            Component c = getParent();

            //  Keep scrolling of the row table in sync with the main table.
            if (c instanceof JViewport) {
                JViewport viewport = (JViewport) c;
                viewport.addChangeListener(this);
            }
        }

        /**
         * Delegate method to main table
         */
        @Override
        public int getRowCount() {
            return Table.this.getRowCount();
        }

        @Override
        public int getRowHeight(int row) {
            return Table.this.getRowHeight(row);
        }

        /**
         * This table does not use any data from the main TableModel,
         * so just return a value based on the row parameter.
         */
        @Override
        public Object getValueAt(int row, int column) {
            if (dataModel instanceof PageTableModel) {
                PageTableModel model = (PageTableModel) dataModel;
                return Integer.valueOf(model.getPage() * model.getPageSize() + row + 1);
            } else {
                return Integer.valueOf(row + 1);
            }
        }

        /**
         * Don't edit data in the main TableModel by mistake
         */
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            //  Keep the scrolling of the row table in sync with main table
            JViewport viewport = (JViewport) e.getSource();
            JScrollPane scrollPane = (JScrollPane) viewport.getParent();
            scrollPane.getVerticalScrollBar().setValue(viewport.getViewPosition().y);
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            //  Keep the row table in sync with the main table
            if ("selectionModel".equals(e.getPropertyName())) {
                setSelectionModel(Table.this.getSelectionModel());
            }

            if ("model".equals(e.getPropertyName())) {
                setModel(Table.this.getModel());
            }
        }
    }
    
    /*
     * Row number renderer. Borrowed from JDK1.4.2 table header
     */
    private static class RowNumberRenderer extends DefaultTableCellRenderer {

        /**
         * Constructor.
         */
        public RowNumberRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (table != null) {
                JTableHeader header = table.getTableHeader();

                if (header != null) {
                    setForeground(header.getForeground());
                    setBackground(header.getBackground());
                    setFont(header.getFont());
                }
            }

            if (isSelected) {
                setFont(getFont().deriveFont(Font.BOLD));
            }

            setText((value == null) ? "" : value.toString());
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));

            return this;
        }
    }
}
