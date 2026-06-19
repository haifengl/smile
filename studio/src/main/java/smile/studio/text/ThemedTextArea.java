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
package smile.studio.text;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import javax.swing.*;
import com.formdev.flatlaf.FlatLaf;
import org.fife.ui.rsyntaxtextarea.*;


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
    }

    /**
     * Applies the theme compatible with system L&F.
     */
    private void applyTheme() {
        if (FlatLaf.isLafDark()) {
            if (DARK_THEME != null) DARK_THEME.apply(this);
        } else {
            if (IDEA_THEME != null) IDEA_THEME.apply(this);
        }
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
