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
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Text area for markdown context.
 *
 * @author Haifeng Li
 */
public class MarkdownArea extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MarkdownArea.class);

    /**
     * Constructor.
     * @param text the markdown text.
     */
    public MarkdownArea(String text) {
        super(new BorderLayout());
        Parser parser = Parser.builder().build();
        Node document = parser.parse(text);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String content = renderer.render(document);

        String html = """
                        <html>
                        <body style="width: 95%; height: auto; margin: 0 auto;">
                        """ + content + "</body></html>";

        try {
            XHTMLPanel browser = new XHTMLPanel();
            browser.setInteractive(false);
            browser.setBackground(getBackground());

            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new InputSource(new StringReader(html)));
            browser.setDocument(doc);
            add(browser, BorderLayout.CENTER);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            logger.error("Failed to process Markdown: ", ex);
            JTextArea area = new JTextArea(text);
            setFont(Monospace.font);
            add(area, BorderLayout.CENTER);
        }
    }
}
