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
import javax.swing.text.StyleContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xhtmlrenderer.layout.SharedContext;
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
public class Markdown extends JPanel implements Scrollable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Markdown.class);
    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
    );
    private static final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    private static float fontSize = SmileStudio.preferences().getFloat("markdownFontSize", 1.25f);
    private final String text;
    private final XHTMLPanel html;
    private JComponent view;

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

    @Override
    public synchronized void addMouseListener(MouseListener l) {
        super.addMouseListener(l);
        if (view != null) {
            view.addMouseListener(l);
        }
    }

    /** Formats a Color as a CSS hex string. */
    private static String toHexColor(Color color) {
        if (color == null) {
            return "#000000";
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Blends two colors by the given ratio of the foreground color (0.0 to 1.0). */
    private static Color blend(Color base, Color overlay, double ratio) {
        double inv = 1.0 - ratio;
        int r = Math.clamp((int) Math.round(base.getRed() * inv + overlay.getRed() * ratio), 0, 255);
        int g = Math.clamp((int) Math.round(base.getGreen() * inv + overlay.getGreen() * ratio), 0, 255);
        int b = Math.clamp((int) Math.round(base.getBlue() * inv + overlay.getBlue() * ratio), 0, 255);
        return new Color(r, g, b);
    }

    /**
     * Converts Markdown text to an HTML fragment using the configured CommonMark + GFM extensions.
     * @param markdown the Markdown text.
     * @return the rendered HTML fragment.
     */
    public static String toHtml(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /** Renders the Markdown content. */
    private XHTMLPanel render() {
        String content = toHtml(text);

        Color fg = UIManager.getColor("Label.foreground");
        if (fg == null) fg = Color.BLACK;
        String fgHex = toHexColor(fg);

        Color panelBg = UIManager.getColor("Panel.background");
        if (panelBg == null) panelBg = Color.WHITE;
        String codeBgHex = toHexColor(blend(panelBg, fg, 0.07));
        String thBgHex = toHexColor(blend(panelBg, fg, 0.05));

        Color border = UIManager.getColor("Component.borderColor");
        String borderHex = toHexColor(border != null ? border : blend(panelBg, fg, 0.25));

        String html = String.format(Locale.ROOT, """
                <html>
                <head>
                    <style>
                        body {
                            width: 95%%;
                            height: auto;
                            margin: 0 auto;
                            color: %s;
                        }
                        .markdown-body {
                            font-size: %.2fem;
                        }
                        table {
                            border-collapse: collapse;
                            margin: 0.6em 0;
                        }
                        th, td {
                            border: 1px solid %s;
                            padding: 6px 10px;
                        }
                        th {
                            font-weight: bold;
                            background-color: %s;
                        }
                        pre, code {
                            font-family: "JetBrains Mono", Consolas, monospace;
                            font-size: 0.85em;
                            background-color: %s;
                            padding: 2px 4px;
                            border-radius: 4px;
                        }
                        pre {
                            padding: 10px;
                            border: 1px solid %s;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                        }
                        pre code {
                            font-size: 1em;
                            background-color: transparent;
                            padding: 0;
                            border-radius: 0;
                        }
                    </style>
                </head>
                <body>
                    <div class="markdown-body">
                        %s
                    </div>
                </body>
                </html>
                """, fgHex, fontSize, borderHex, thBgHex, codeBgHex, borderHex, content);

        try {
            XHTMLPanel browser = new XHTMLPanel() {
                @Override
                public void addNotify() {
                    super.addNotify();
                    if (getParent() instanceof Markdown md
                            && md.getParent() instanceof JViewport vp
                            && vp.getParent() instanceof JScrollPane scrollPane) {
                        setEnclosingScrollPane(scrollPane);
                    }
                }

                @Override
                protected void relayout() {
                    super.relayout();
                    registerCompositeFonts(getSharedContext());
                }
            };
            browser.setInteractive(false);
            browser.setOpaque(false); // transparent background
            registerCompositeFonts(browser.getSharedContext());

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
            view = browser;
            return browser;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            logger.error("Failed to process Markdown: {}", ex.getMessage());
            var area = new ThemedTextArea(text);
            add(area, BorderLayout.CENTER);
            view = area;
        }
        return null;
    }

    /**
     * Registers Swing composite fonts for physical monospace font families so that
     * code blocks and inline code retain OS fallback for emoji and Unicode symbols.
     */
    private static void registerCompositeFonts(SharedContext sharedContext) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        for (String family : new String[]{"JetBrains Mono", "Consolas", "Menlo", "DejaVu Sans Mono"}) {
            Font composite = sc.getFont(family, Font.PLAIN, 1);
            if (composite.getFamily().equalsIgnoreCase(family)) {
                sharedContext.setFontMapping(family, composite);
            }
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

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return html != null ? html.getPreferredScrollableViewportSize() : getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return html != null ? html.getScrollableUnitIncrement(visibleRect, orientation, direction) : 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return html != null ? html.getScrollableBlockIncrement(visibleRect, orientation, direction) : 64;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return html == null || html.getScrollableTracksViewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return html != null && html.getScrollableTracksViewportHeight();
    }
}
