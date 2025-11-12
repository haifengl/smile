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

import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import smile.plot.swing.Palette;

/**
 * Text area for cell output.
 *
 * @author Haifeng Li
 */
public class OutputArea extends JTextArea {
    private final DefaultHighlightPainter painter = new DefaultHighlightPainter(Palette.LIGHT_PINK);
    private final Pattern pattern = Pattern.compile("ERROR|WARN|Recoverable issue|Rejected snippet|Unresolved dependencies|Exception:");

    /**
     * Constructor.
     */
    public OutputArea() {
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
    }

    @Override
    public void setText(String text) {
        super.setText(text);
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
        } catch (BadLocationException e) {
            System.err.println(e.getMessage());
        }
    }
}
