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

import java.io.IOException;
import com.formdev.flatlaf.FlatLaf;
import org.fife.ui.rsyntaxtextarea.*;

/**
 * UI theme for text editor or area.
 *
 * @author Haifeng Li
 */
public interface DarkTheme {
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DarkTheme.class);
    /** Dark theme. */
    Theme DARK_THEME = load("dark");
    /** Druid theme. */
    Theme DRUID_THEME = load("druid");
    /** Eclipse theme. */
    Theme ECLIPSE_THEME = load("eclipse");
    /** IntelliJ IDEA theme. */
    Theme IDEA_THEME = load("idea");
    /** Monokai theme. */
    Theme MONOKAI_THEME = load("monokai");
    /** Visual Studio theme. */
    Theme VS_THEME = load("vs");

    /**
     * Applies the dark theme to a text area.
     * @param textArea the text area to be dark.
     */
    static void apply(RSyntaxTextArea textArea) {
        if (FlatLaf.isLafDark() && DARK_THEME != null) {
            DARK_THEME.apply(textArea);
        }
    }

    /**
     * Loads a built-in theme.
     *
     * @param theme the theme name.
     * @return the theme.
     */
    static Theme load(String theme) {
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
