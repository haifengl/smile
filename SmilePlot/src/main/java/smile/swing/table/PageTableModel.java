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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

import smile.swing.Button;

/**
 * A table model that performs "paging" of its data. This model
 * reports a small number of rows (like 100 or so) as a "page" of data. You
 * can switch pages to view all of the rows as needed using the pageDown()
 * and pageUp() methods. Presumably, access to the other pages of data is
 * dictated by other GUI elements such as up/down buttons, or maybe a text
 * field that allows you to enter the page number you want to display.
 * 
 * @author Haifeng LI
 */
@SuppressWarnings("serial")
public abstract class PageTableModel extends AbstractTableModel {

    /**
     * Number of rows per page.
     */
    private int pageSize = 100;
    /**
     * The current page.
     */
    private int page = 0;
    /**
     * Associate toolbar for page control.
     */
    private JToolBar toolbar;
    /**
     * Controls on toolbar.
     */
    private JTextField pageSizeField = new JTextField(5);
    private JTextField pageField = new JTextField(5);
    private JLabel pageSizeLabel = new JLabel("Page Size: ");
    private JLabel totalRowCountLabel = new JLabel();
    private JLabel pageCountLabel = new JLabel();
    private String totalRowCountLabelFormat =  "Total Rows: %-8d    Page:";
    private String pageCountLabelFormat = " of %d";

    /**
     * Paging event action.
     */
    private Action pageDownAction = new PageDownAction();
    private Action pageUpAction = new PageUpAction();
    private Action firstPageAction = new FirstPageAction();
    private Action lastPageAction = new LastPageAction();
    private Action gotoPageAction = new GoToPageAction();
    private Action pageSizeAction = new PageSizeAction();


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
        initToolBar();
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
     * The sub class should implement this method to return the real number
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
     * Returns a tool bar to control the plot.
     * @return a tool bar to control the plot.
     */
    public JToolBar getToolbar() {
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
        
        TableModelListener listener = new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent tme) {
                if (tme.getType() == TableModelEvent.INSERT || tme.getType() == TableModelEvent.DELETE) {
                    if (getPage() >= getPageCount()) {
                        setPage(getPageCount() - 1);
                    }

                    totalRowCountLabel.setText(String.format(totalRowCountLabelFormat, getRealRowCount()));
                    pageField.setText(Integer.toString(getPage() + 1));
                    pageCountLabel.setText(String.format(pageCountLabelFormat, getPageCount()));
                }
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

        public PageDownAction() {
            super("Next Page", new ImageIcon(PageTableModel.class.getResource("/smile/swing/images/navigate_right.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            pageDown();
            setActionEnabled();
        }
    }
    
    class PageUpAction extends AbstractAction {

        public PageUpAction() {
            super("Previous Page", new ImageIcon(PageTableModel.class.getResource("/smile/swing/images/navigate_left.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            pageUp();
            setActionEnabled();
        }
    }
    
    class FirstPageAction extends AbstractAction {

        public FirstPageAction() {
            super("First Page", new ImageIcon(PageTableModel.class.getResource("/smile/swing/images/navigate_beginning.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setPage(0);
            setActionEnabled();
        }
    }
    
    class LastPageAction extends AbstractAction {

        public LastPageAction() {
            super("Last Page", new ImageIcon(PageTableModel.class.getResource("/smile/swing/images/navigate_end.png")));
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