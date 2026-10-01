/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.text;

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
    private final XHTMLPanel html;

    /**
     * Constructor.
     * @param text the markdown text.
     */
    public Markdown(String text) {
        super(new BorderLayout());
        this.text = text;
        this.html = render();
    }

    /** Returns the original Markdown text. */
    public String text() {
        return text;
    }

    /**
     * Returns the content pane for rendering Markdown. */
    public XHTMLPanel contentPane() {
        return html;
    }

    /** Renders the Markdown content. */
    private XHTMLPanel render() {
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
                        logger.error("Failed to open browser: {}", ex.getMessage());
                    }
                }
            });

            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new InputSource(new StringReader(html)));
            browser.setDocument(doc);
            add(browser, BorderLayout.CENTER);
            return browser;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            logger.error("Failed to process Markdown: {}", ex.getMessage());
            var area = new ThemedTextArea(text);
            add(area, BorderLayout.CENTER);
        }
        return null;
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
