/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import smile.studio.model.CommandType;
import static smile.studio.model.CommandType.*;

/**
 * The combobox of command type without a visible arrow button.
 *
 * @author Haifeng Li
 */
public class CommandTypeComboBox extends JComboBox<CommandType> {
    /**
     * Constructor.
     */
    public CommandTypeComboBox() {
        super(new CommandType[] {Raw, Magic, Shell, Python, Markdown, Instructions});
        // Select Instructions by default.
        setSelectedItem(Instructions);
        // Calculate the preferred width.
        setPrototypeDisplayValue(Instructions);
        // Don't allow typing in the text field.
        setEditable(false);
        setBorder(BorderFactory.createEmptyBorder());
        setRenderer(new ItemRenderer());

        setUI(new BasicComboBoxUI() {
            @Override
            protected BasicComboPopup createPopup() {
                return new BasicComboPopup(comboBox) {
                    @Override
                    protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
                        // Sets desired width for popup list
                        return super.computePopupBounds(px, py, 120, ph);
                    }
                };
            }
        });

        // Find and remove the arrow button component
        for (Component component : getComponents()) {
            if (component instanceof AbstractButton) {
                remove(component);
                revalidate(); // layout manager needs to recalculate the sizes
                break; // Assuming only one button
            }
        }

        // To prevent the box from expanding
        setMaximumSize(getPreferredSize());
    }

    /** Custom renderer controls how each item is displayed in the dropdown list. */
    private static class ItemRenderer extends JLabel implements ListCellRenderer<CommandType> {
        public ItemRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CommandType> list,
                                                      CommandType value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            setOpaque(index != -1);
            if (value == null) {
                setText(""); // Handle null value if necessary
            }

            if (value != null) {
                if (index == -1) { // This is the selected item in the "box mode"
                    setText(value.legend());
                } else { // This is an item in the dropdown list
                    setText(value.description());
                }
            } else {
                setText(""); // Handle null value if necessary
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
    }
}
