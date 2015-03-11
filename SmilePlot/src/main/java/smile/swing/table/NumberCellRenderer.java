/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.swing.table;

import java.text.NumberFormat;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Number renderer in JTable.
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NumberCellRenderer extends DefaultTableCellRenderer {
    public static final NumberCellRenderer INTEGER  = new NumberCellRenderer(NumberFormat.getIntegerInstance());
    public static final NumberCellRenderer NUMBER   = new NumberCellRenderer(NumberFormat.getNumberInstance());
    public static final NumberCellRenderer PERCENT  = new NumberCellRenderer(NumberFormat.getPercentInstance());
    public static final NumberCellRenderer CURRENCY = new NumberCellRenderer(NumberFormat.getCurrencyInstance());

    /**
     * Format string.
     */
    private NumberFormat numberFormat;

    /**
     * Constructor.
     */
    public NumberCellRenderer() {
        numberFormat = NumberFormat.getInstance();
        init();
    }
    
    /**
     * Constructor.
     * @param precision The number of digits after the decimal separator.
     */
    public NumberCellRenderer(int precision) {
        numberFormat = NumberFormat.getNumberInstance();
        init();
        numberFormat.setMinimumFractionDigits(precision);
    }

    /**
     * Constructor.
     * @param numberFormat The number format.
     */
    public NumberCellRenderer(NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
        init();
    }

    /**
     * Initialize the format.
     */
    private void init() {
        setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);        
    }
    
    /**
     * Returns the number format used for rendering.
     * @return The number format used for rendering.
     */
    public NumberFormat getNumberFormat() {
        return numberFormat;
    }
    
    @Override
    public void setValue(Object value) {
        setText((value == null) ? "" : numberFormat.format(value));
   }
}
