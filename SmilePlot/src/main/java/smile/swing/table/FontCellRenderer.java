/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing.table;

import java.awt.Font;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Font renderer in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class FontCellRenderer extends DefaultTableCellRenderer {
    
    /**
     * Constructor.
     */
    public FontCellRenderer() {
        this("AaBbYyZz");
    }
    
    /**
     * Constructor.
     */
    public FontCellRenderer(String text) {
        setText(text);
    }
    
    @Override
    public void setValue(Object value) {
        if (value == null) {
            setText("");
        }
        
        Font font = (Font) value;
        setFont(font);
        
        String tooltip = font.getFontName() + ", " + font.getSize();
        
        setToolTipText(tooltip);
    }
}