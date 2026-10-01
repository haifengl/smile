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
import java.awt.event.*;
import java.nio.file.Path;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.*;
import smile.io.Paths;
import smile.util.lsp.LanguageService;

/**
 * Text editor with syntax highlighting.
 *
 * @author Haifeng Li
 */
public class Editor extends ThemedTextArea {
    /** Auto-completion provider. */
    private LspCompletionProvider provider;

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
        setSyntaxEditingStyle(style);
        setLineWrap(true);
        setWrapStyleWord(true);
        if (!style.equals(SYNTAX_STYLE_NONE)) {
            setCodeFoldingEnabled(true);
            setTabSize(4);
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

    @Override
    public void setText(String t) {
        super.setText(t);
        if (provider != null) {
            provider.change(this);
        }
    }

    /**
     * Closes the autocomplete provider.
     */
    public void close() {
        if (provider != null) {
            provider.close();
        }
    }

    /**
     * Sets up auto-completion based on the file type.
     * @param fileUrl the file URL to set up auto-completion for.
     * @param style the syntax style for the file.
     */
    public void setAutoComplete(String fileUrl, String style) {
        var lang = switch (style) {
            case SYNTAX_STYLE_JAVA -> "java";
            case SYNTAX_STYLE_PYTHON -> "python";
            default -> "";
        };
        var lsp = LanguageService.get(lang);
        if (lsp == null) return;

        provider = new LspCompletionProvider(lsp.server().getTextDocumentService(), fileUrl);
        provider.open(this, lang);
        getDocument().addDocumentListener(provider);
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(300); // 300ms delay
        ac.install(this);

        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke('.'), "autocomplete");
        // Trigger auto-completion when '.' is typed,
        // which is common for member access in many languages.
        actionMap.put("autocomplete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                    insert(".", getCaretPosition());
                    ac.doCompletion();
            }
        });
    }

    /**
     * Probes the syntax style based on the filename extension.
     * @param file the file to probe.
     * @return the syntax style for the file, or {@code SYNTAX_STYLE_NONE} if unknown.
     */
    public static String probeSyntaxStyle(Path file) {
        var fileName = file.getFileName() != null ? file.getFileName().toString() : "";
        if (fileName.equalsIgnoreCase("Welcome") || fileName.equalsIgnoreCase("Release Notes")) {
            return SYNTAX_STYLE_MARKDOWN;
        }

        return switch (Paths.getFileExtension(file)) {
            case "md" -> SYNTAX_STYLE_MARKDOWN;
            case "java" -> SYNTAX_STYLE_JAVA;
            case "py" -> SYNTAX_STYLE_PYTHON;
            case "sql" -> SYNTAX_STYLE_SQL;
            case "scala", "sc" -> SYNTAX_STYLE_SCALA;
            case "kt", "kts" -> SYNTAX_STYLE_KOTLIN;
            case "cpp", "cxx" -> SYNTAX_STYLE_CPLUSPLUS;
            case "c" -> SYNTAX_STYLE_C;
            case "js" -> SYNTAX_STYLE_JAVASCRIPT;
            case "ts" -> SYNTAX_STYLE_TYPESCRIPT;
            case "rs" -> SYNTAX_STYLE_RUST;
            case "csv", "tsv" -> SYNTAX_STYLE_CSV;
            case "json", "jsonl" -> SYNTAX_STYLE_JSON;
            case "sh" -> SYNTAX_STYLE_UNIX_SHELL;
            case "bat" -> SYNTAX_STYLE_WINDOWS_BATCH;
            case "ps1" -> SYNTAX_STYLE_POWERSHELL;
            case "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "xml" -> SYNTAX_STYLE_XML;
            case "yaml", "yml" -> SYNTAX_STYLE_YAML;
            case "properties" -> SYNTAX_STYLE_PROPERTIES_FILE;
            default -> SYNTAX_STYLE_NONE;
        };
    }
}
