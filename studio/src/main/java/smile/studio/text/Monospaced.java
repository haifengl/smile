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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;
import javax.swing.*;
import smile.studio.SmileStudio;

/**
 * The monospaced font for view components.
 *
 * <p>Font-size changes fire a bound property named {@code "font"} so that
 * all registered {@link PropertyChangeListener}s (e.g. editors, output areas)
 * can update themselves automatically.
 */
public class Monospaced {
    /** Minimum allowed font size in points. */
    public static final float MIN_FONT_SIZE = 8f;
    /** Maximum allowed font size in points. */
    public static final float MAX_FONT_SIZE = 32f;

    /** The text attributes for the monospaced font. */
    private static final Map<TextAttribute, Object> attributes = Map.of(
            TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD
    );

    /** The shared font; persisted via {@link SmileStudio#preferences()}. */
    private static Font font = baseFont()
            .deriveFont(attributes)
            .deriveFont(Math.clamp(SmileStudio.preferences().getFloat("monospaced", 14f),
                    MIN_FONT_SIZE, MAX_FONT_SIZE));

    /** A stable source object for font-change events. */
    private static final Object bean = new Object();
    /** Property-change support. */
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(bean);

    /** Private constructor – utility class. */
    private Monospaced() {
    }

    /**
     * Returns a stable base monospaced font even when the look-and-feel
     * does not define {@code monospaced.font} (e.g. headless test runs).
     */
    private static Font baseFont() {
        Font uiMonospaced = UIManager.getFont("monospaced.font");
        if (uiMonospaced != null) {
            return uiMonospaced;
        }

        Font textAreaFont = UIManager.getFont("TextArea.font");
        if (textAreaFont != null) {
            return new Font(Font.MONOSPACED, textAreaFont.getStyle(), textAreaFont.getSize());
        }

        return new Font(Font.MONOSPACED, Font.PLAIN, 14);
    }

    /**
     * Adds a {@link PropertyChangeListener} to the listener list.
     * @param listener the listener to be added.
     */
    public static void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a {@link PropertyChangeListener} from the listener list.
     * @param listener the listener to be removed.
     */
    public static void removeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Returns the current monospace font.
     * @return the current monospace font.
     */
    public static Font getFont() {
        return font;
    }

    /**
     * Sets the monospace font, fires a {@code "font"} property-change event,
     * and persists the new size via {@link SmileStudio#preferences()}.
     * @param newFont the new monospace font.
     */
    public static void setFont(Font newFont) {
        Font oldFont = font;
        font = newFont;
        pcs.firePropertyChange("font", oldFont, newFont);
        SmileStudio.preferences().putFloat("monospaced", font.getSize2D());
    }

    /**
     * Adjusts the font size by {@code delta} points, clamped to
     * [{@value #MIN_FONT_SIZE}, {@value #MAX_FONT_SIZE}].
     * @param delta the number of points to add (negative = smaller).
     */
    public static void adjustFontSize(float delta) {
        setFont(font.deriveFont(Math.clamp(font.getSize2D() + delta,
                MIN_FONT_SIZE, MAX_FONT_SIZE)));
    }
}
