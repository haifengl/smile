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

import org.fife.ui.rsyntaxtextarea.*;

/**
 * Code editor with syntax highlighting.
 *
 * @author Haifeng Li
 */
public class CodeEditor extends RSyntaxTextArea {
    /**
     * Constructor.
     */
    public CodeEditor() {
        super(20, 80);
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        setCodeFoldingEnabled(true);
        setTabSize(4);
        setLineWrap(false);
        setWrapStyleWord(false);
    }

    /**
     * Sets the number of rows to fit the content.
     */
    public void setPreferredRows() {
        setRows(Math.min(20, getLineCount()));
    }
}
