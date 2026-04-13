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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;
import javax.swing.*;

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
    private static Font font = UIManager.getFont("monospaced.font")
            .deriveFont(attributes)
            .deriveFont(Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE,
                    SmileStudio.preferences().getFloat("monospaced", 14f))));

    /** A singleton instance used as the source object for font-change events. */
    private static final Monospaced bean = new Monospaced();
    /** Property-change support. */
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(bean);

    /** Private constructor – utility class. */
    private Monospaced() {
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
        setFont(font.deriveFont(
                Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, font.getSize2D() + delta))));
    }
}
