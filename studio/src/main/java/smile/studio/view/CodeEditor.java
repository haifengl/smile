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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import org.fife.ui.rsyntaxtextarea.*;

/**
 * Code editor with syntax highlighting.
 *
 * @author Haifeng Li
 */
public class CodeEditor extends RSyntaxTextArea {
    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     * @param style language highlighting style.
     */
    public CodeEditor(int rows, int cols, String style) {
        super(rows, cols);
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setSyntaxEditingStyle(style);
        if (!style.equals(SyntaxConstants.SYNTAX_STYLE_NONE)) {
            setCodeFoldingEnabled(true);
            setTabSize(4);
            setLineWrap(false);
            setWrapStyleWord(false);
        }

        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "increase-font-size");
        actionMap.put("increase-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Monospaced.adjustFontSize(1);
                Intent.adjustFontSize(0.1f);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "decrease-font-size");
        actionMap.put("decrease-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Monospaced.adjustFontSize(-1);
                Intent.adjustFontSize(-0.1f);
            }
        });
    }

    /**
     * Sets the number of rows to fit the content.
     */
    public void setPreferredRows() {
        setRows(Math.min(30, getLineCount()));
    }
}
