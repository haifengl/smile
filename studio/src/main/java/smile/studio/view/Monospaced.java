package smile.studio.view;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;
import javax.swing.*;
import smile.studio.SmileStudio;

/**
 * The monospaced font for view components.
 */
public class Monospaced {
    /** The text attributes for the monospaced font. */
    private static final Map<TextAttribute, Object> attributes = Map.of(
            TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD
    );
    /** The shared font for consistency. */
    private static Font font = UIManager.getFont("monospaced.font")
            .deriveFont(attributes)
            .deriveFont(SmileStudio.preferences().getFloat("monospaced", 14));

    /** A singleton instance used as the source object for font changes. */
    private static final Monospaced bean = new Monospaced();
    /** The utility to support bound properties. */
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(bean);

    /** Private constructor to prevent creating instances. */
    private Monospaced() {

    }

    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param listener the PropertyChangeListener to be added.
     */
    public static void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param listener the PropertyChangeListener to be removed.
     */
    public static void removeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Returns the monospace font.
     * @return the monospace font.
     */
    public static Font getFont() {
        return font;
    }

    /**
     * Sets the monospace font.
     * @param newFont the new monospace font.
     */
    public static void setFont(Font newFont) {
        Font oldFont = font;
        font = newFont;
        pcs.firePropertyChange("font", oldFont, newFont);
        SmileStudio.preferences().putInt("monospaced", font.getSize());
    }

    /**
     * Adjusts the font size.
     * @param delta the value by which the font size is adjusted.
     */
    public static void adjustFontSize(float delta) {
        setFont(font.deriveFont(Math.min(32f, Math.max(8f, font.getSize() + delta))));
    }
}
