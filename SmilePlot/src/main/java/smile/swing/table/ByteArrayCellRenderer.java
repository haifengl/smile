/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing.table;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Byte array renderer in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ByteArrayCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructor.
     */
    public ByteArrayCellRenderer() {
    }
    
    @Override
    public void setValue(Object value) {
        if (value == null) {
            setText("");
            return;
        }
        
        byte[] data = (byte[]) value;
        
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