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

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.formdev.flatlaf.ui.FlatLineBorder;
import smile.plot.swing.Palette;

/**
 * A command is a multiline text input field, and its contents can be executed
 * by an LLM model.
 *
 * @author Haifeng Li
 */
public class Command extends JPanel {
    private static final Color inputColor = new Color(220, 248, 198);
    private static final Color borderColor = Palette.web("#8dd4e8");
    private final JPanel inputPane = new JPanel(new BorderLayout());
    private final JLabel prompt = new JLabel(">", SwingConstants.CENTER);
    private final JTextArea input = new JTextArea(1, 60);
    private final JTextArea output = new JTextArea();

    public Command(Analyst analyst) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));
        prompt.setVerticalAlignment(JLabel.TOP);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setBackground(inputColor);
        inputPane.setBackground(inputColor);
        inputPane.setBorder(createRoundBorder());
        inputPane.add(prompt, BorderLayout.WEST);
        inputPane.add(input, BorderLayout.CENTER);

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        add(inputPane, BorderLayout.NORTH);
        add(output, BorderLayout.CENTER);

        InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = input.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "run");
        actionMap.put("run", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (input.isEditable()) {
                    analyst.run(Command.this);
                }
            }
        });

        prompt.setFont(Monospace.getFont());
        input.setFont(Monospace.getFont());
        output.setFont(Monospace.getFont());
    }

    /**
     * Sets whether the input area should be editable.
     * @param editable the editable flag.
     */
    public void setEditable(boolean editable) {
        input.setEditable(editable);
        if (editable) {
            prompt.setText(">");
            input.setBackground(inputColor);
            inputPane.setBackground(inputColor);

        } else {
            prompt.setText("*");
            input.setBackground(getBackground());
            inputPane.setBackground(getBackground());
        }
    }

    /**
     * Sets the text color for input and prompt.
     * @param color the foreground color.
     */
    public void setInputForeground(Color color) {
        prompt.setForeground(color);
        input.setForeground(color);
    }

    /**
     * Sets the font for input and prompt.
     * @param font the font.
     */
    public void setInputFont(Font font) {
        prompt.setFont(font);
        input.setFont(font);
    }

    /**
     * Returns the command prompt.
     * @return the command prompt.
     */
    public JLabel prompt() {
        return prompt;
    }

    /**
     * Returns the command input.
     * @return the command input.
     */
    public JTextArea input() {
        return input;
    }

    /**
     * Returns the command output.
     * @return the command output.
     */
    public JTextArea output() {
        return output;
    }

    /**
     * Returns a border with round corners.
     * @return a border with round corner.
     */
    static FlatLineBorder createRoundBorder() {
        return new FlatLineBorder(new Insets(5, 5, 5, 5),
                borderColor, 1, 20);
    }
}
