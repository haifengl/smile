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
        setSelectedItem(CommandType.Instructions);
        setEditable(false); // Don't allow typing in the text field.
        setBorder(BorderFactory.createEmptyBorder());
        setRenderer(new ItemRenderer());
        //setPreferredSize(renderer.getPreferredSize());


        // Extend BasicComboBoxUI and override the createArrowButton() method
        // to return a button with zero width and height, effectively making
        // it invisible.
        BasicComboBoxUI ui = new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                // Create an invisible button
                JButton button = new JButton();
                var dim = new Dimension(0, 0);
                button.setPreferredSize(dim);
                button.setMinimumSize(dim);
                button.setMaximumSize(dim);
                button.setVisible(false); // Ensure it's not visible
                return button;
            }

            @Override
            protected BasicComboPopup createPopup() {
                return new BasicComboPopup(comboBox) {
                    @Override
                    protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
                        int desiredWidth = 120;
                        return super.computePopupBounds(px, py, desiredWidth, ph);
                    }
                };
            }
        };
        setUI(ui);
    }

    /** Custom renderer controls how each item is displayed in the dropdown list. */
    private class ItemRenderer extends JLabel implements ListCellRenderer<CommandType> {
        public ItemRenderer() {
            setOpaque(true);
            //setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.TOP);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CommandType> list,
                                                      CommandType value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
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
