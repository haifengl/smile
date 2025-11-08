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
import java.awt.*;
import java.awt.event.ActionEvent;
import com.formdev.flatlaf.ui.FlatBorder;
import smile.plot.swing.Palette;

/**
 * An architect creates model building pipeline.
 *
 * @author Haifeng Li
 */
public class Architect extends JPanel {
    static final Color userMessageColor = new Color(220, 248, 198);
    static final Color botMessageColor = Palette.web("#8dd4e8");
    final JPanel messages = new JPanel();
    final JTextArea input = new JTextArea();

    /**
     * Constructor.
     */
    public Architect() {
        super(new BorderLayout(0, 10));
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(messages);
        add(scrollPane, BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH);

        input.setRows(3);
        input.setBorder(new FlatBorder());

        InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = input.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        actionMap.put("send-message", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
    }

    /**
     * Sends user message.
     */
    private void sendMessage() {
        String message = input.getText().trim();
        if (!message.isEmpty()) {
            addMessage(message, userMessageColor, 0.1f);
            input.setText("");
        }
    }

    /**
     * Adds a message widget.
     */
    private void addMessage(String message, Color background, float alignment) {
        if (!message.isEmpty()) {
            JTextPane pane = new JTextPane();
            pane.setText(message);
            pane.setBackground(background);
            pane.setBorder(new FlatBorder());
            pane.setAlignmentX(alignment);
            messages.add(pane);
        }
    }
}
