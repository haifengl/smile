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

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.border.Border;

import java.awt.Color;
import java.awt.Component;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Color renderer in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ColorCellRenderer extends DefaultTableCellRenderer {

    /**
     * Border when not selected.
     */
    Border unselectedBorder = null;
    /**
     * Border when selected.
     */
    Border selectedBorder = null;
    /**
     * True if has border.
     */
    boolean isBordered = true;

    /**
     * Constructor.
     */
    public ColorCellRenderer() {
        this(true);
    }
    
    /**
     * Constructor.
     * @param isBordered true to show border.
     */
    public ColorCellRenderer(boolean isBordered) {
        this.isBordered = isBordered;
        setOpaque(true); // MUST do this for background to show up. 
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value == null) {
            return this;
        }
        
        Color color = (Color) value;
        setBackground(color);
        
        if (isBordered) {
            if (isSelected) {
                if (selectedBorder == null) {
                    selectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, table.getSelectionBackground());
                }
                setBorder(selectedBorder);
            } else {
                if (unselectedBorder == null) {
                    unselectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, table.getBackground());
                }
                setBorder(unselectedBorder);
            }
        }
        
        setToolTipText("RGB (" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ")");
        return this;
    }
}