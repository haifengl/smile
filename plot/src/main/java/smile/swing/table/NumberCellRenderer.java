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
