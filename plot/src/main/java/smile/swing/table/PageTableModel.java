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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.Objects;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

import smile.swing.Button;
import smile.swing.SmileSwing;

/**
 * A table model that performs "paging" of its data. This model
 * reports a small number of rows (like 100 or so) as a "page" of data. You
 * can switch pages to view all the rows as needed using the pageDown()
 * and pageUp() methods. Presumably, access to the other pages of data is
 * dictated by other GUI elements such as up/down buttons, or maybe a text
 * field that allows you to enter the page number you want to display.
 * 
 * @author Haifeng Li
 */
public abstract class PageTableModel extends AbstractTableModel {
    /**
     * Number of rows per page.
     */
    private int pageSize;
    /**
     * The current page.
     */
    private int page = 0;
    /**
     * Associate toolbar for page control.
     */
    private JToolBar toolbar;
    /** Page size field on toolbar. */
    private final JTextField pageSizeField = new JTextField(5);
    /** Page field on toolbar. */
    private final JTextField pageField = new JTextField(5);
    /** Page size label on toolbar. */
    private final JLabel pageSizeLabel = new JLabel("Page Size: ");
    /** Row count label on toolbar. */
    private final JLabel totalRowCountLabel = new JLabel();
    /** Page count label on toolbar. */
    private final JLabel pageCountLabel = new JLabel();
    /** Row count label format. */
    private final String totalRowCountLabelFormat =  "Total Rows: %-8d    Page: ";
    /** Page count label format. */
    private final String pageCountLabelFormat = " of %d";

    /** Page down event action. */
    private final Action pageDownAction = new PageDownAction();
    /** Page up event action. */
    private final Action pageUpAction = new PageUpAction();
    /** First page event action. */
    private final Action firstPageAction = new FirstPageAction();
    /** Last page event action. */
    private final Action lastPageAction = new LastPageAction();
    /** Goto page event action. */
    private final Action gotoPageAction = new GoToPageAction();
    /** Page size event action. */
    private final Action pageSizeAction = new PageSizeAction();

    /**
     * Default constructor.
     */
    public PageTableModel() {
        this(100);
    }

    /**
     * Constructor.
     * @param pageSize The number of rows per page. 
     */
    public PageTableModel(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public int getRowCount() {
        if (page == getPageCount() - 1) {
            return getRealRowCount() - pageSize * (getPageCount() - 1);
        } else {
            return Math.min(pageSize, getRealRowCount());
        }
    }

    /**
     * The subclass should implement this method to return the real number
     * of rows in the model.
     * @return The real number of rows in the model.
     */
    public abstract int getRealRowCount();
    
    /**
     * Returns the row number of data given the row number of current page.
     * @param row the row number in the current page.
     * @return the row number in the whole date set.
     */
    public int getRealRow(int row) {
        return row + (page * pageSize);
    }
    
    @Override
    public Object getValueAt(int row, int col) {
        int realRow = row + (page * pageSize);
        return getValueAtRealRow(realRow, col);
    }

    /**
     * Returns the value for the cell at real row index. 
     * @param row the real row whose value is to be queried.
     * @param col the column whose value is to be queried.
     * @return the value Object at the specified cell
     */
    public abstract Object getValueAtRealRow(int row, int col);
    

    /**
     * Returns the current page.
     * @return the current page.
     */
    public int getPage() {
        return page;
    }

    /**
     * Moves to specific page and fire a data changed (all rows).
     * @param p the page number.
     * @return true if we can move to the page.
     */
    public boolean setPage(int p) {
        if (p >= 0 && p < getPageCount()) {
            page = p;
            fireTableDataChanged();
            return true;
        }
        
        return false;
    }

    /**
     * Returns the number of pages.
     * @return the number of pages.
     */
    public int getPageCount() {
        int pages = (int) Math.ceil((double) getRealRowCount() / pageSize);
        
        if (pages == 0) {
            pages = 1;
        }
        
        return pages;
    }

    /**
     * Returns the page size.
     * @return the page size. 
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Sets the page size.
     * @param s the page size.
     */
    public void setPageSize(int s) {
        if (s <= 0) {
            throw new IllegalArgumentException("non-positive page size: " + s);
        }
    
        if (s == pageSize) {
            return;
        }
        
        int oldPageSize = pageSize;
        pageSize = s;
        page = (oldPageSize * page) / pageSize;
        fireTableDataChanged();
    }

    /**
     * Moves to next page and fire a data changed (all rows).
     * @return true if we can move to next page.
     */
    public boolean pageDown() {
        if (page < getPageCount() - 1) {
            page++;
            fireTableDataChanged();
            return true;
        }
        
        return false;
    }

    /**
     * Moves to previous page and fire a data changed (all rows).
     * @return true if we can move to previous page.
     */
    public boolean pageUp() {
        if (page > 0) {
            page--;
            fireTableDataChanged();
            return true;
        }
        
        return false;
    }

    /**
     * Returns a toolbar to control the plot.
     * @return a toolbar to control the plot.
     */
    public JToolBar getToolbar() {
        if (toolbar == null) {
            initToolBar();
        }
        return toolbar;
    }

    /**
     * Initialize context menu and toolbar.
     */
    private void initToolBar() {
        toolbar = new JToolBar();
        
        toolbar.add(new Button(firstPageAction));
        toolbar.add(new Button(pageUpAction));
        toolbar.add(new Button(pageDownAction));
        toolbar.add(new Button(lastPageAction));
        
        toolbar.addSeparator();
        toolbar.add(pageSizeLabel);
        toolbar.add(pageSizeField);
        pageSizeField.setText(Integer.toString(getPageSize()));
        pageSizeField.setHorizontalAlignment(JTextField.RIGHT);
        pageSizeField.setAction(pageSizeAction);
        pageSizeField.setMaximumSize(pageSizeField.getPreferredSize());
        
        toolbar.addSeparator();
        totalRowCountLabel.setText(String.format(totalRowCountLabelFormat, getRealRowCount()));
        toolbar.add(totalRowCountLabel);
        
        toolbar.add(pageField);
        pageField.setText(Integer.toString(getPage() + 1));
        pageField.setHorizontalAlignment(JTextField.RIGHT);
        pageField.setAction(gotoPageAction);
        pageField.setMaximumSize(pageField.getPreferredSize());
        
        pageCountLabel.setText(String.format(pageCountLabelFormat, getPageCount()));
        toolbar.add(pageCountLabel);
        
        setActionEnabled();
        
        TableModelListener listener = event -> {
            if (event.getType() == TableModelEvent.INSERT || event.getType() == TableModelEvent.DELETE) {
                if (getPage() >= getPageCount()) {
                    setPage(getPageCount() - 1);
                }

                totalRowCountLabel.setText(String.format(totalRowCountLabelFormat, getRealRowCount()));
                pageField.setText(Integer.toString(getPage() + 1));
                pageCountLabel.setText(String.format(pageCountLabelFormat, getPageCount()));
            }
        };
        
        addTableModelListener(listener);
    }

    private void setActionEnabled() {
        if (getPage() == 0) {
            firstPageAction.setEnabled(false);
            pageUpAction.setEnabled(false);
        } else {
            firstPageAction.setEnabled(true);
            pageUpAction.setEnabled(true);            
        }
        
        if (getPage() == getPageCount() - 1) {
            lastPageAction.setEnabled(false);
            pageDownAction.setEnabled(false);
        } else {
            lastPageAction.setEnabled(true);
            pageDownAction.setEnabled(true);            
        }
        
        totalRowCountLabel.setText(String.format(totalRowCountLabelFormat, getRealRowCount()));
        pageField.setText(Integer.toString(getPage() + 1));
        pageCountLabel.setText(String.format(pageCountLabelFormat, getPageCount()));
    }
    
    class PageDownAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(PageTableModel.class.getResource("/smile/swing/images/forward.png")));
        static final ImageIcon icon16 = SmileSwing.scale(icon, 16);
        static final ImageIcon icon24 = SmileSwing.scale(icon, 24);

        public PageDownAction() {
            super("Next Page", icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            pageDown();
            setActionEnabled();
        }
    }
    
    class PageUpAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(PageTableModel.class.getResource("/smile/swing/images/back.png")));
        static final ImageIcon icon16 = SmileSwing.scale(icon, 16);
        static final ImageIcon icon24 = SmileSwing.scale(icon, 24);

        public PageUpAction() {
            super("Previous Page", icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            pageUp();
            setActionEnabled();
        }
    }
    
    class FirstPageAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(PageTableModel.class.getResource("/smile/swing/images/double-left.png")));
        static final ImageIcon icon16 = SmileSwing.scale(icon, 16);
        static final ImageIcon icon24 = SmileSwing.scale(icon, 24);

        public FirstPageAction() {
            super("First Page", icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setPage(0);
            setActionEnabled();
        }
    }
    
    class LastPageAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(PageTableModel.class.getResource("/smile/swing/images/double-right.png")));
        static final ImageIcon icon16 = SmileSwing.scale(icon, 16);
        static final ImageIcon icon24 = SmileSwing.scale(icon, 24);

        public LastPageAction() {
            super("Last Page", icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setPage(getPageCount() - 1);
            setActionEnabled();
        }
    }
    
    class GoToPageAction extends AbstractAction {

        public GoToPageAction() {
            super("Go To Page");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                int p = Integer.parseInt(pageField.getText()) - 1;
                if (setPage(p)) {
                    setActionEnabled();                
                } else {
                    Toolkit.getDefaultToolkit().beep();                
                }
            } catch (Exception ex) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
    
    class PageSizeAction extends AbstractAction {

        public PageSizeAction() {
            super("Page Size");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                int s = Integer.parseInt(pageSizeField.getText());
                setPageSize(s);
                setActionEnabled();
            } catch (Exception ex) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
