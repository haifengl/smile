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
package smile.studio.font;

import com.formdev.flatlaf.util.FontUtils;

/**
 * The JetBrains Mono font family.
 *
 * @author Haifeng Li
 */
public interface JetBrainsMonoFont {
    String FAMILY = "JetBrains Mono";
    String STYLE_REGULAR = "JetBrainsMono-Regular.ttf";
    String STYLE_ITALIC = "JetBrainsMono-Italic.ttf";
    String STYLE_BOLD = "JetBrainsMono-Bold.ttf";
    String STYLE_BOLD_ITALIC = "JetBrainsMono-BoldItalic.ttf";
    String STYLE_EXTRA_BOLD = "JetBrainsMono-ExtraBold.ttf";
    String STYLE_EXTRA_BOLD_ITALIC = "JetBrainsMono-ExtraBoldItalic.ttf";
    String STYLE_LIGHT = "JetBrainsMono-Light.ttf";
    String STYLE_LIGHT_ITALIC = "JetBrainsMono-LightItalic.ttf";
    String STYLE_EXTRA_LIGHT = "JetBrainsMono-ExtraLight.ttf";
    String STYLE_EXTRA_LIGHT_ITALIC = "JetBrainsMono-ExtraLightItalic.ttf";
    String STYLE_MEDIUM = "JetBrainsMono-Medium.ttf";
    String STYLE_MEDIUM_ITALIC = "JetBrainsMono-MediumItalic.ttf";
    String STYLE_SEMI_BOLD = "JetBrainsMono-SemiBold.ttf";
    String STYLE_SEMI_BOLD_ITALIC = "JetBrainsMono-SemiBoldItalic.ttf";
    String STYLE_THIN = "JetBrainsMono-Thin.ttf";
    String STYLE_THIN_ITALIC = "JetBrainsMono-ThinItalic.ttf";

    /**
     * Registers the fonts for lazy loading.
     */
    static void register() {
        FontUtils.registerFontFamilyLoader(FAMILY, JetBrainsMonoFont::install);
    }

    /**
     * Installs the fonts for all styles.
     */
    static void install() {
        install(STYLE_REGULAR);
        install(STYLE_ITALIC);
        install(STYLE_BOLD);
        install(STYLE_BOLD_ITALIC);
        install(STYLE_EXTRA_BOLD);
        install(STYLE_EXTRA_BOLD_ITALIC);
        install(STYLE_LIGHT);
        install(STYLE_LIGHT_ITALIC);
        install(STYLE_EXTRA_LIGHT);
        install(STYLE_EXTRA_LIGHT_ITALIC);
        install(STYLE_MEDIUM);
        install(STYLE_MEDIUM_ITALIC);
        install(STYLE_SEMI_BOLD);
        install(STYLE_SEMI_BOLD_ITALIC);
        install(STYLE_THIN);
        install(STYLE_THIN_ITALIC);
    }

    /**
     * Installs the font for the given style.
     */
    static boolean install(String name) {
        return FontUtils.installFont(JetBrainsMonoFont.class.getResource(name));
    }
}
