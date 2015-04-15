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
