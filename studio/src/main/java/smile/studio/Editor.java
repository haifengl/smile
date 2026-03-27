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

import javax.swing.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.*;
import smile.io.Paths;

/**
 * Text editor with syntax highlighting.
 *
 * @author Haifeng Li
 */
public class Editor extends RSyntaxTextArea {
    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     */
    public Editor(int rows, int cols) {
        this(rows, cols, SyntaxConstants.SYNTAX_STYLE_NONE);
    }

    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     * @param style language highlighting style.
     */
    public Editor(int rows, int cols, String style) {
        super(rows, cols);
        putClientProperty("FlatLaf.styleClass", "monospaced");
        setSyntaxEditingStyle(style);
        if (!style.equals(SYNTAX_STYLE_NONE)) {
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
                Markdown.adjustFontSize(0.1f);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "decrease-font-size");
        actionMap.put("decrease-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Monospaced.adjustFontSize(-1);
                Markdown.adjustFontSize(-0.1f);
            }
        });
    }

    /** Probes the syntax style based on the file content type. */
    public static String probeSyntaxStyle(Path file) {
        try {
            return switch (Files.probeContentType(file)) {
                case "text/markdown" -> SYNTAX_STYLE_MARKDOWN;
                case "text/x-java-source" -> SYNTAX_STYLE_JAVA;
                case "text/x-python" -> SYNTAX_STYLE_PYTHON;
                case "application/sql" -> SYNTAX_STYLE_SQL;
                case "text/x-scala" -> SYNTAX_STYLE_SCALA;
                case "text/x-kotlin" -> SYNTAX_STYLE_KOTLIN; // less standardized
                case "text/x-c++src" -> SYNTAX_STYLE_CPLUSPLUS;
                case "text/x-csrc" -> SYNTAX_STYLE_C;
                case "text/x-javascript" -> SYNTAX_STYLE_JAVASCRIPT;
                case "text/x-rustsrc" -> SYNTAX_STYLE_RUST;
                case "text/csv" -> SYNTAX_STYLE_CSV;
                case "text/json", "text/x-json", "application/json" -> SYNTAX_STYLE_JSON;
                case "application/x-sh" -> SYNTAX_STYLE_UNIX_SHELL;
                case "application/bat", "application/x-bat" -> SYNTAX_STYLE_WINDOWS_BATCH;
                case "text/html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
                case "text/xml", "application/xml" -> SYNTAX_STYLE_XML;
                case "application/yaml" -> SYNTAX_STYLE_YAML;
                default -> switch (Paths.getFileExtension(file)) {
                    case "kt", "kts" -> SYNTAX_STYLE_KOTLIN;
                    case "bat" -> SYNTAX_STYLE_WINDOWS_BATCH;
                    case "properties" -> SYNTAX_STYLE_PROPERTIES_FILE;
                    default -> SYNTAX_STYLE_NONE;
                };
            };
        } catch (Exception ex) {
            return SYNTAX_STYLE_NONE;
        }
    }
}
