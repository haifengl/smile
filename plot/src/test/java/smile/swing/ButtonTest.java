/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing;

import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Button}.
 */
public class ButtonTest {

    private static Action buildAction(String name, ImageIcon icon) {
        return new AbstractAction(name, icon) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    }

    @Test
    public void testButtonWithIconHidesText() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIcon icon = new ImageIcon(img);
        Action action = buildAction("My Action", icon);
        Button btn = new Button(action);

        assertNull(btn.getText(), "Text should be null when an icon is set");
        assertNotNull(btn.getIcon(), "Icon should be present");
    }

    @Test
    public void testButtonWithIconUsesShortDescriptionAsTooltip() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIcon icon = new ImageIcon(img);
        Action action = buildAction("Action Name", icon);
        action.putValue(Action.SHORT_DESCRIPTION, "My Tooltip");
        Button btn = new Button(action);

        assertEquals("My Tooltip", btn.getToolTipText());
    }

    @Test
    public void testButtonWithIconFallsBackToNameAsTooltip() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIcon icon = new ImageIcon(img);
        Action action = buildAction("Fallback Name", icon);
        // No SHORT_DESCRIPTION set
        Button btn = new Button(action);

        assertEquals("Fallback Name", btn.getToolTipText());
    }

    @Test
    public void testButtonWithoutIconKeepsText() {
        // No icon → text should remain
        Action action = buildAction("Text Only", null);
        Button btn = new Button(action);

        assertEquals("Text Only", btn.getText());
    }

    @Test
    public void testButtonIsEnabledByDefault() {
        Action action = buildAction("Enabled", null);
        Button btn = new Button(action);
        assertTrue(btn.isEnabled());
    }

    @Test
    public void testButtonReflectsDisabledAction() {
        Action action = buildAction("Disabled", null);
        action.setEnabled(false);
        Button btn = new Button(action);
        assertFalse(btn.isEnabled());
    }
}

