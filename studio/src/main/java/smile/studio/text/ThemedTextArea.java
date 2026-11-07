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

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import javax.swing.*;
import javax.swing.text.Caret;
import javax.swing.text.Document;

import com.formdev.flatlaf.FlatLaf;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.ConfigurableCaret;


/**
 * Themed text editor or area.
 *
 * @author Haifeng Li
 */
public class ThemedTextArea extends RSyntaxTextArea {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ThemedTextArea.class);
    /** Dark theme. */
    private static final Theme DARK_THEME = loadTheme("dark");
    /** Druid theme. */
    private static final Theme DRUID_THEME = loadTheme("druid");
    /** Eclipse theme. */
    private static final Theme ECLIPSE_THEME = loadTheme("eclipse");
    /** IntelliJ IDEA theme. */
    private static final Theme IDEA_THEME = loadTheme("idea");
    /** Monokai theme. */
    private static final Theme MONOKAI_THEME = loadTheme("monokai");
    /** Visual Studio theme. */
    private static final Theme VS_THEME = loadTheme("vs");

    /**
     * Constructor.
     */
    public ThemedTextArea() {
        initTheme();
    }

    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     */
    public ThemedTextArea(int rows, int cols) {
        super(rows, cols);
        initTheme();
    }

    /**
     * Constructor of uneditable text area.
     * @param text the text.
     */
    public ThemedTextArea(String text) {
        super(text);
        setEditable(false);
        initTheme();
    }

    /**
     * Initializes the theme and listens for global Look and Feel changes.
     */
    private void initTheme() {
        putClientProperty("FlatLaf.styleClass", "monospaced");

        applyTheme();
        // Listen for global Look and Feel changes
        UIManager.addPropertyChangeListener(evt -> {
            if ("lookAndFeel".equals(evt.getPropertyName())) {
                applyTheme();
            }
        });

        // Listen for monospace font size changes.
        Monospaced.addListener((e) ->
                SwingUtilities.invokeLater(() -> setFont((Font) e.getNewValue())));

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

        // Safe localized caret visibility fix without setBlinkRate(),
        // which alters shared states globally.
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                Caret caret = getCaret();
                if (caret != null) {
                    caret.setVisible(isEditable());
                    caret.setSelectionVisible(isEditable());
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                Caret caret = getCaret();
                if (caret != null) {
                    caret.setVisible(false);
                    caret.setSelectionVisible(false);
                }
            }
        });

        // Set caret invisible when text area becomes showing
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                Caret caret = getCaret();
                if (caret != null && isShowing()) {
                    caret.setVisible(false);
                }
            }
        });
    }

    @Override
    protected Document createDefaultModel() {
        return new RSyntaxDocument(SYNTAX_STYLE_NONE) {
            @Override
            public Token getTokenListForLine(int line) {
                return coalesceUnicodeTokens(super.getTokenListForLine(line));
            }
        };
    }

    /**
     * Coalesces adjacent tokens when JFlex's 16-bit char scanner splits UTF-16
     * surrogate pairs, emoji variation selectors / ZWJ / combining sequences, or
     * non-ASCII identifier runs into separate single-char tokens.
     *
     * @param firstToken the head of the token list for a line.
     * @return the head of the coalesced token list.
     */
    static Token coalesceUnicodeTokens(Token firstToken) {
        Token curr = firstToken;
        while (curr instanceof TokenImpl currImpl && currImpl.isPaintable()) {
            Token next = currImpl.getNextToken();
            if (next instanceof TokenImpl nextImpl
                    && nextImpl.isPaintable()
                    && currImpl.text != null
                    && currImpl.text == nextImpl.text
                    && currImpl.textCount > 0
                    && nextImpl.textCount > 0
                    && currImpl.textOffset + currImpl.textCount == nextImpl.textOffset
                    && currImpl.getOffset() + currImpl.textCount == nextImpl.getOffset()
                    && shouldCoalesce(currImpl, nextImpl)) {
                currImpl.textCount += nextImpl.textCount;
                currImpl.setNextToken(nextImpl.getNextToken());
            } else {
                curr = next;
            }
        }
        return firstToken;
    }

    private static boolean shouldCoalesce(TokenImpl curr, TokenImpl next) {
        char lastChar = curr.text[curr.textOffset + curr.textCount - 1];
        char firstChar = next.text[next.textOffset];

        // Never split a UTF-16 surrogate pair across tokens.
        if (Character.isHighSurrogate(lastChar) && Character.isLowSurrogate(firstChar)) {
            return true;
        }

        // Keep emoji variation selectors, ZWJ sequences, and combining marks with their base glyph.
        if (!curr.isWhitespace() && !next.isWhitespace()) {
            if (lastChar == '\u200D' || isGraphemeContinuation(next.text, next.textOffset, next.textCount)) {
                return true;
            }
        }

        // Coalesce adjacent non-hyperlink IDENTIFIER tokens emitted one char at a time by JFlex fallback rules.
        return curr.getType() == TokenTypes.IDENTIFIER
                && next.getType() == TokenTypes.IDENTIFIER
                && curr.isHyperlink() == next.isHyperlink()
                && curr.getLanguageIndex() == next.getLanguageIndex();
    }

    private static boolean isGraphemeContinuation(char[] text, int offset, int count) {
        char ch = text[offset];
        if (ch == '\u200D' || ch == '\u20E3' || (ch >= '\uFE00' && ch <= '\uFE0F')) {
            return true;
        }
        int cp = (Character.isHighSurrogate(ch) && count > 1 && Character.isLowSurrogate(text[offset + 1]))
                ? Character.toCodePoint(ch, text[offset + 1])
                : ch;
        if ((cp >= 0x1F3FB && cp <= 0x1F3FF) || (cp >= 0xE0100 && cp <= 0xE01EF)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /**
     * Applies the theme compatible with system L&F.
     */
    private void applyTheme() {
        Theme theme = FlatLaf.isLafDark() ? DARK_THEME : IDEA_THEME;
        if (theme != null) {
            theme.apply(this);
            if (theme.scheme != null) {
                setSyntaxScheme((SyntaxScheme) theme.scheme.clone());
            }
        }
        setFont(Monospaced.getFont());
    }

    /**
     * Loads a built-in theme.
     *
     * @param theme the theme name.
     * @return the theme.
     */
    private static Theme loadTheme(String theme) {
        try {
            return Theme.load(RSyntaxTextArea.class.getResourceAsStream(
                    "/org/fife/ui/rsyntaxtextarea/themes/" + theme + ".xml"
            ));
        } catch (IOException ex) {
            logger.error("Failed to load {} theme: {}", theme, ex.getMessage());
        }
        return null;
    }
}
