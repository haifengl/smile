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

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

/**
 * Text area for cell output.
 *
 * @author Haifeng Li
 */
public class OutputArea extends JTextArea {
    /**
     * Constructor.
     */
    public OutputArea() {
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);

        var doc = ((AbstractDocument) getDocument());
        doc.setDocumentFilter(new HighlightDocumentFilter());
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
                    getHighlighter().addHighlight(startIndex + 1, startIndex + match.length() - 1, highlightPainter);
                }
            }
        }
    }
}
