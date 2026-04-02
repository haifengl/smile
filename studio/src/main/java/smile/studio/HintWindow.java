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
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import smile.plot.swing.Palette;
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
                        int line = getLineOfOffset(editor, dot);
                        int start = getOffsetOfLine(editor, line);
                        String lead = editor.getText(start, dot).trim();
                        String hint = hints.get(lead);
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
     * Returns the line number given a text offset.
     * @param editor the text component.
     * @param offset the text offset.
     * @return the line number.
     */
    private int getLineOfOffset(JTextComponent editor, int offset) {
        return editor.getDocument().getDefaultRootElement().getElementIndex(offset);
    }

    /**
     * Returns the start offset of a line.
     * @param editor the text component.
     * @param line the line number.
     * @return the start offset.
     */
    private int getOffsetOfLine(JTextComponent editor, int line) {
        return editor.getDocument().getDefaultRootElement().getElement(line).getStartOffset();
    }

    /**
     * Returns the word ending at the text offset.
     * @param editor the text component.
     * @param offset the text offset.
     * @return the word ending at the text offset.
     */
    private String getWord(JTextComponent editor, int offset) throws BadLocationException {
        int line = getLineOfOffset(editor, offset);
        int start = getOffsetOfLine(editor, line);
        String text = editor.getText(start, offset);
        String[] words = text.trim().split("\\s+");
        if (words.length > 0) {
            return words[words.length - 1];
        }
        return "";
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
            if (rect == null) return;

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
