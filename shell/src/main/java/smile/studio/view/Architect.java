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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import com.formdev.flatlaf.ui.FlatBorder;
import com.formdev.flatlaf.ui.FlatLineBorder;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import smile.plot.swing.Palette;

/**
 * An architect creates model building pipeline.
 *
 * @author Haifeng Li
 */
public class Architect extends JPanel {
    static final Color userMessageColor = new Color(220, 248, 198);
    static final Color botMessageColor = Palette.web("#8dd4e8");
    static final FlatBorder flat = new FlatBorder(); // proxy to get theme color and width
    final JPanel messages = new JPanel();
    final JTextArea input = new JTextArea();

    /**
     * Constructor.
     */
    public Architect() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 0, 0, 8));
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(messages);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel inputPane = new JPanel(new BorderLayout());
        inputPane.setBackground(input.getBackground());
        inputPane.setBorder(createRoundBorder());
        inputPane.add(input, BorderLayout.CENTER);

        input.setRows(3);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);

        InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = input.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        actionMap.put("send-message", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "new-line");
        actionMap.put("new-line", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                input.insert("\n", input.getCaretPosition());
            }
        });

        add(scrollPane, BorderLayout.CENTER);
        add(inputPane, BorderLayout.SOUTH);
    }

    /**
     * Sends user message.
     */
    private void sendMessage() {
        String message = input.getText().trim();
        if (!message.isEmpty()) {
            addUserMessage(message, userMessageColor);
            input.setText("");
        }
    }

    /**
     * Adds a message widget.
     */
    private void addAgentMessage(String message, Color background) {
        if (!message.isEmpty()) {
            // Parse Markdown to HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(message);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String html = renderer.render(document);

            JTextPane text = new JTextPane();
            text.setContentType("text/html");
            text.setText(html);
            text.setEditable(false);
            Dimension size = getSize();
            text.setPreferredSize(new Dimension(size.width * 9 / 10, 100));
            //text.setBackground(background);

            JPanel pane = new JPanel(new BorderLayout());
            pane.setBorder(new CompoundBorder(
                    new EmptyBorder(8, 8, 8, 8),
                    createRoundBorder()));
            //pane.setBackground(background);
            pane.add(text, BorderLayout.CENTER);
            messages.add(pane);
        }
    }

    /**
     * Adds a user message widget.
     */
    private void addUserMessage(String message, Color background) {
        if (!message.isEmpty()) {
            JTextArea text = new JTextArea();
            text.setText(message);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setRows(text.getLineCount());

            JPanel pane = new JPanel(new BorderLayout());
            pane.setBorder(new CompoundBorder(
                    new EmptyBorder(8, 8, 8, 16),
                    createRoundBorder()));
            //pane.setBackground(background);
            pane.add(text, BorderLayout.CENTER);
            messages.add(pane);
        }
    }

    /**
     * Returns a border with round corners.
     * @return a border with round corner.
     */
    private FlatLineBorder createRoundBorder() {
        return new FlatLineBorder(new Insets(5, 5, 5, 5),
                (Color) flat.getStyleableValue("borderColor"),
                (Float) flat.getStyleableValue("borderWidth"),
                20);
    }
}
