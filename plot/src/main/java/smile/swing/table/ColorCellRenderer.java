/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
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
     * True if it has border.
     */
    final boolean isBordered;

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