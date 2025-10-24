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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

/**
 * Interactive environment to write and execute Java code combining code,
 * documentation, and visualizations. The notebook consists of a sequence
 * of cells.
 *
 * @author Haifeng Li
 */
public class Notebook extends JPanel implements KeyListener {
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 72;
    private int fontSize = 12;
    private Font font = new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, fontSize);

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isControlDown()) {
            if (e.getKeyCode() == KeyEvent.VK_PLUS || e.getKeyCode() == KeyEvent.VK_EQUALS) {
                if (fontSize < MAX_FONT_SIZE) {
                    fontSize++;
                }
            } else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
                if (fontSize > MIN_FONT_SIZE) {
                    fontSize--;
                }
            }
            font = new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, fontSize);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}
