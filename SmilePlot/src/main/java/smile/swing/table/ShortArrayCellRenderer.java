/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing.table;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Short array renderer in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ShortArrayCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructor.
     */
    public ShortArrayCellRenderer() {
    }
    
    @Override
    public void setValue(Object value) {
        if (value == null) {
            setText("");
        }
        
        short[] data = (short[]) value;
        
        StringBuilder builder = new StringBuilder();
        if (data.length > 0) {
            builder.append("[").append(data[0]);
        }
        
        for (int i = 1; i < data.length; i++) {
            builder.append(", ").append(data[i]);
        }
        builder.append("]");
        setText(builder.toString());
    }
}