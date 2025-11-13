package smile.studio.view;

import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.FontUtils;

/**
 * The monospace font for view components.
 */
public class Monospace {
    /** The shared font for consistency. */
    private static Font font = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 14);
    /** A singleton instance used as the source object for font changes. */
    private static final Monospace bean = new Monospace();
    /** The utility to support bound properties. */
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(bean);

    /** Private constructor to prevent creating instances. */
    private Monospace() {

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
    }
}
