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
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.StringReader;

import com.formdev.flatlaf.ui.FlatBorder;
import com.formdev.flatlaf.ui.FlatLineBorder;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xml.sax.InputSource;
import smile.plot.swing.Palette;

/**
 * An architect creates model building pipeline.
 *
 * @author Haifeng Li
 */
public class Analyst extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Analyst.class);
    private static final Color userMessageColor = new Color(220, 248, 198);
    private static final Color botMessageColor = Palette.web("#8dd4e8");
    private static final FlatBorder flat = new FlatBorder(); // proxy to get theme color and width
    private final JPanel messages = new JPanel();
    private final JTextArea input = new JTextArea(3, 80);

    /**
     * Constructor.
     */
    public Analyst() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 0, 0, 8));
        messages.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));//new BoxLayout(messages, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(messages);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel inputPane = new JPanel(new BorderLayout());
        inputPane.setBackground(input.getBackground());
        inputPane.setBorder(createRoundBorder());
        inputPane.add(input, BorderLayout.CENTER);

        //input.setRows(3);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        messages.add(inputPane);

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
        //add(inputPane, BorderLayout.SOUTH);
    }

    /**
     * Sends user message.
     */
    private void sendMessage() {
        String message = input.getText().trim();
        if (!message.isEmpty()) {
            input.setText("");
            addUserMessage(message);
        }
    }

    /**
     * Adds an agent message widget.
     */
    private JComponent addAgentMessage(String message) {
        if (!message.isEmpty()) {
            // Parse Markdown to HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(message);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String content = renderer.render(document);

            try {
                String html = """
                        <html>
                        <body style="width: 95%; height: auto; margin: 0 auto;">
                        """ + content + "</body></html>";
                var factory = DocumentBuilderFactory.newInstance();
                var builder = factory.newDocumentBuilder();
                var doc = builder.parse(new InputSource(new StringReader(html)));

                XHTMLPanel browser = new XHTMLPanel();
                browser.setInteractive(false);
                browser.setBackground(getBackground());
                browser.setDocument(doc, null); // The second argument is for base URI, can be null
                return browser;
            } catch (Exception ex) {
                logger.error("Failed to add agent message: ", ex);
                return new JLabel(ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Adds a user message widget.
     */
    private void addUserMessage(String message) {
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
            pane.add(text, BorderLayout.CENTER);
            messages.add(pane);

            var response = addAgentMessage(message);
            if (response != null) {
                pane.add(response, BorderLayout.CENTER);
            }
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
