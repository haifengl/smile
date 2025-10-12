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

import java.awt.Color;
import java.awt.Dimension;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serial;
import javax.swing.*;
import javax.swing.text.*;

/**
 * Text area for log messages.
 *
 * @author Haifeng Li
 */
public class LogArea extends JPanel {
    @Serial
    private static final long serialVersionUID = 2L;

    /** Text area of log messages. */
    private final JTextArea logText;
    /** Output stream for redirection. */
    private final OutputStream out = new OutputStream() {
        @Override
        public void write(int b) {
            append(String.valueOf((char) b));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            append(new String(b, off, len));
        }

        @Override
        public void write(byte[] b) {
            write(b, 0, b.length);
        }
    };

    /**
     * Constructor.
     */
    public LogArea() {
        logText = new JTextArea();
        logText.setEditable(false);
        var doc = ((AbstractDocument) logText.getDocument());
        doc.setDocumentFilter(new HighlightDocumentFilter());

        JScrollPane logView = new JScrollPane(logText);
        Dimension minimumSize = new Dimension(100, 50);
        logView.setMinimumSize(minimumSize);
        add(logView);
    }

    /**
     * Returns the output stream directing to the log area.
     * @return the output stream.
     */
    public OutputStream getOutputStream() {
        return out;
    }

    /**
     * Appends text to log area.
     * @param text log message.
     */
    private void append(String text) {
        SwingUtilities.invokeLater(() -> logText.append(text));
    }

    private class HighlightDocumentFilter extends DocumentFilter {
        private final DefaultHighlighter.DefaultHighlightPainter highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);

        public HighlightDocumentFilter() {
            //SimpleAttributeSet background = new SimpleAttributeSet();
            //StyleConstants.setBackground(background, Color.RED);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            super.replace(fb, offset, length, text, attrs);

            String match = " ERROR ";
            int startIndex = offset - match.length();
            if (startIndex >= 0) {
                String last = fb.getDocument().getText(startIndex, match.length()).trim();
                if (last.equals(match)) {
                    logText.getHighlighter().addHighlight(startIndex + 1, startIndex + match.length() - 1, highlightPainter);
                }
            }
        }
    }
}
