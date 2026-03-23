/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.LinkListener;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import smile.studio.SmileStudio;

/**
 * A component to render Markdown text.
 *
 * @author Haifeng Li
 */
public class Markdown extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Markdown.class);
    private static final Parser parser = Parser.builder().build();
    private static final HtmlRenderer renderer = HtmlRenderer.builder().build();
    private static float fontSize = SmileStudio.preferences().getFloat("markdownFontSize", 1.25f);
    private final String text;

    /**
     * Constructor.
     * @param text the markdown text.
     */
    public Markdown(String text) {
        super(new BorderLayout());
        this.text = text;
        render();
    }

    /** Renders the Markdown content. */
    private void render() {
        Node document = parser.parse(text);
        String content = renderer.render(document);

        String html = """
                      <html>
                      <body style="width: 95%; height: auto; margin: 0 auto;">
                      <div style="font-size:"""
                + String.format(" %.2fem;\">", fontSize)
                + content + "</div></body></html>";

        try {
            XHTMLPanel browser = new XHTMLPanel();
            browser.setInteractive(false);
            browser.setOpaque(false); // transparent background

            // Remove pre-installed LinkListeners
            for (var listener : browser.getMouseTrackingListeners()) {
                if (listener instanceof LinkListener) {
                    browser.removeMouseTrackingListener(listener);
                }
            }
            // Add a custom LinkListener to handle link clicks
            browser.addMouseTrackingListener(new LinkListener() {
                @Override
                public void linkClicked(BasicPanel panel, String uri) {
                    try {
                        // Use the Java Desktop API to open the URI in the default browser
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(new URI(uri));
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to open browser: ", ex);
                    }
                }
            });

            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new InputSource(new StringReader(html)));
            browser.setDocument(doc);
            add(browser, BorderLayout.CENTER);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            logger.error("Failed to process Markdown: ", ex);
            JTextArea area = new JTextArea(text);
            Monospaced.addListener((e) ->
                    SwingUtilities.invokeLater(() -> setFont((Font) e.getNewValue())));
            setFont(Monospaced.getFont());
            add(area, BorderLayout.CENTER);
        }
    }

    /**
     * Adjusts the font size to render Markdown.
     * @param delta the value by which the font size is adjusted.
     */
    public static void adjustFontSize(float delta) {
        fontSize += delta;
        SmileStudio.preferences().putFloat("markdownFontSize", fontSize);
    }
}
