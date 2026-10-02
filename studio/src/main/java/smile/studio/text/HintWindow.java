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
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import smile.plot.swing.Palette;
import smile.swing.SmileUtilities;
import smile.util.Strings;

/**
 * A window to display a hint at the caret position in a JTextComponent.
 *
 * @author Haifeng Li
 */
public class HintWindow extends JWindow {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HintWindow.class);
    private final JLabel hintLabel = new JLabel();
    private final Map<String, String> hints;

    /**
     * Constructor.
     * @param owner the window from which the hint window is displayed.
     * @param hints a map from trigger words to hint messages.
     */
    public HintWindow(Window owner, Map<String, String> hints) {
        super(owner);
        this.hints = hints;
        hintLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        // Pale cream background
        hintLabel.setBackground(Palette.web("FFFEE4"));
        hintLabel.setOpaque(true);
        add(hintLabel);
    }

    /**
     * Registers a text component to the hint window.
     * @param editor the text component to register.
     */
    public void addEditor(JTextComponent editor) {
        // Add a key listener to show the hint when the space key is pressed.
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    try {
                        int dot = editor.getCaretPosition();
                        String lead = leadingText(editor, dot).trim();
                        String hint = hintFor(hints, lead);
                        if (Strings.isNullOrBlank(hint)) {
                            setVisible(false);
                        } else {
                            show(editor, hint, dot);
                            hintLabel.setText(hint);
                            pack();
                            if (!isVisible()) setVisible(true);
                        }
                    }  catch (BadLocationException ex) {
                        logger.warn(ex.getMessage());
                    }
                }
            }
        });

        // Add a focus listener to hide the hint when the editor loses focus
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                setVisible(false);
            }
        });
        pack();
    }

    /**
     * Returns the text on the caret's line, from the start of the line up to the caret.
     * This is the candidate trigger word for a hint (for example {@code /memory}).
     * <p>{@link JTextComponent#getText(int, int)} takes a length, not an end offset, so
     * the range is {@code [start, dot)} -- not {@code [start, start + dot)}, which reads
     * past the end of the document and throws {@link BadLocationException}. Package-private
     * for testing.
     * @param editor the text component.
     * @param dot the caret position.
     * @return the line prefix from the line start up to the caret.
     */
    static String leadingText(JTextComponent editor, int dot) throws BadLocationException {
        int line = SmileUtilities.getLineOfOffset(editor, dot);
        int start = SmileUtilities.getOffsetOfLine(editor, line);
        // The range never includes the line's newline: a newline sits at the start
        // offset of the following line, and the caret's line begins after it.
        return editor.getText(start, dot - start);
    }

    /**
     * Resolves the hint for the text typed so far on the line. Matches the longest
     * trigger that is a prefix of the text, so an argument typed after a command keeps
     * its hint: {@code "/memory"} matches while typing {@code /memory add}, and
     * {@code "/memory add"} takes over once the full argument is present. An exact key
     * still wins. Matching the longest prefix rather than the first also keeps Map
     * iteration order irrelevant. Package-private for testing.
     * @param hints the trigger-to-hint map.
     * @param text the line text up to the caret.
     * @return the hint, or null when no trigger matches.
     */
    static String hintFor(Map<String, String> hints, String text) {
        if (text.isEmpty()) {
            return null;
        }
        String best = null;
        for (Map.Entry<String, String> entry : hints.entrySet()) {
            String trigger = entry.getKey();
            if (text.equals(trigger)) {
                return entry.getValue();
            }
            if (text.startsWith(trigger) && (best == null || trigger.length() > best.length())) {
                best = trigger;
            }
        }
        return best == null ? null : hints.get(best);
    }

    /**
     * Shows the hint window at the caret position of text component.
     * @param editor the text component.
     * @param hint the hint message.
     * @param dot the caret position.
     */
    private void show(JTextComponent editor, String hint, int dot) {
        try {
            // Get the pixel coordinates of the caret position
            var rect = editor.modelToView2D(dot);
            if (rect == null) {
                // modelToView2D returns null when the caret cannot be mapped (for
                // example the component is not yet displayed); nothing to anchor to.
                setVisible(false);
                return;
            }

            // Position the hint window relative to the JTextComponent
            Point locationOnScreen = editor.getLocationOnScreen();
            int x = (int) (locationOnScreen.x + rect.getX());
            int y = (int) (locationOnScreen.y + rect.getY() + rect.getHeight()); // Display below the caret
            setLocation(x, y);

            hintLabel.setText(hint);
            pack();
            if (!isVisible()) setVisible(true);
        } catch (BadLocationException ex) {
            logger.warn(ex.getMessage());
        }
    }
}
