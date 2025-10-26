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

public class Chat extends JPanel {
    final JTextArea messages = new JTextArea();
    final JTextField input = new JTextField();
    final JButton send = new JButton("Send");

    public Chat() {
        super(new BorderLayout());
        messages.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messages);
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPane = new JPanel(new BorderLayout());
        inputPane.add(input, BorderLayout.CENTER);
        inputPane.add(send, BorderLayout.EAST);
        add(inputPane, BorderLayout.SOUTH);

        send.addActionListener(e -> sendMessage());
        // Enter key action
        input.addActionListener(e -> sendMessage());
    }

    private void sendMessage() {
        String message = input.getText().trim();
        if (!message.isEmpty()) {
            messages.append(message);
            input.setText("");
        }
    }
}
