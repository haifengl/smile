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
package smile.studio;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import smile.plot.swing.Palette;

/**
 * Text area for output stream.
 *
 * @author Haifeng Li
 */
public class OutputArea extends RSyntaxTextArea implements HyperlinkListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OutputArea.class);
    private final DefaultHighlightPainter painter = new DefaultHighlightPainter(Palette.LIGHT_PINK);
    private final Pattern pattern = Pattern.compile("ERROR|WARN|Recoverable issue|Rejected snippet|Unresolved dependencies|Exception:");
    /** The output buffer. StringBuffer is multi-thread safe while StringBuilder isn't. */
    final StringBuffer buffer = new StringBuffer();

    /**
     * Constructor.
     */
    public OutputArea() {
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);

        // RSyntaxTextArea related settings.
        setSyntaxEditingStyle(SYNTAX_STYLE_MARKDOWN);
        setHighlightCurrentLine(false);
        // Set transparent background
        setBackground(new Color(255, 255, 255, 0));
        setOpaque(false);
        // Enable hyperlink support
        setHyperlinksEnabled(true);
        // Remove the requirement for a modifier key (default CTRL) to activate hyperlinks
        //setLinkScanningMask(0);
        addHyperlinkListener(this);
    }

    /**
     * Returns the output buffer.
     * @return the output buffer.
     */
    public StringBuffer buffer() {
        return buffer;
    }

    /**
     * Flushes the buffer to the text area.
     */
    public void flush() {
        SwingUtilities.invokeLater(() -> setText(buffer.toString()));
    }

    /**
     * Clears the text content and buffer.
     */
    public void clear() {
        buffer.setLength(0);
        SwingUtilities.invokeLater(() -> setText(""));
    }

    /**
     * Appends a string to the buffer.
     *
     * @param str a string.
     * @return this object.
     */
    public OutputArea print(String str) {
        buffer.append(str);
        return this;
    }

    /**
     * Appends a line to buffer and flush context to the output area.
     *
     * @param str a string.
     * @return this object.
     */
    public OutputArea println(String str) {
        buffer.append(str).append('\n');
        flush();
        return this;
    }

    /**
     * Appends a newline to buffer and flush context to the output area.
     *
     * @return this object.
     */
    public OutputArea println() {
        buffer.append('\n');
        flush();
        return this;
    }

    /**
     * Highlights lines with errors or issues.
     */
    public void highlight() {
        String text = getText();
        try {
            var highlighter = getHighlighter();
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                var matcher = pattern.matcher(lines[i]);
                if (matcher.find()) {
                    int startOffset = getLineStartOffset(i);
                    int endOffset = getLineEndOffset(i);
                    highlighter.addHighlight(startOffset, endOffset, painter);
                }
            }
        } catch (BadLocationException ex) {
            logger.warn("Failed to add highlight: {}", ex.getMessage());
        }
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            // A link was clicked
            try {
                // Open the URL in the system's default browser
                Desktop.getDesktop().browse(new URI(e.getURL().toString()));
            } catch (IOException | URISyntaxException ex) {
                JOptionPane.showMessageDialog(this,
                        "Cannot open URL: " + e.getURL().toString(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
