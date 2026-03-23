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
package smile.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Represents a set of character entity references defined by the
 * HTML 4.0 standard.
 *
 * @author Haifeng Li
 */
class HtmlCharacter {
    static final String DEFAULT_ENCODING = "ISO-8859-1";

    private static final String PROPERTIES_FILE = "HtmlCharacterEntityReferences.properties";

    private static final char REFERENCE_START = '&';

    private static final char REFERENCE_END = ';';

    private final String[] characterToEntityReferenceMap = new String[3000];

    /**
     * Returns a new set of character entity references reflecting the HTML 4.0 character set.
     */
    public HtmlCharacter() {
        Properties entityReferences = new Properties();

        // Load reference definition file
        InputStream is = HtmlCharacter.class.getResourceAsStream(PROPERTIES_FILE);
        if (is == null) {
            throw new IllegalStateException(
                    "Cannot find reference definition file [HtmlCharacterEntityReferences.properties] as class path resource");
        }
        try {
            try {
                entityReferences.load(is);
            } finally {
                is.close();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to parse reference definition file [HtmlCharacterEntityReferences.properties]: " + ex.getMessage());
        }

        // Parse reference definition properties
        Enumeration<?> keys = entityReferences.propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            int referredChar = Integer.parseInt(key);
            int index = (referredChar < 1000 ? referredChar : referredChar - 7000);
            String reference = entityReferences.getProperty(key);
            this.characterToEntityReferenceMap[index] = REFERENCE_START + reference + REFERENCE_END;
        }
    }

    /**
     * Return the reference mapped to the given character, or {@code null} if none found.
     */
    public String escape(char character) {
        return escape(character, DEFAULT_ENCODING);
    }

    /**
     * Return the reference mapped to the given character, or {@code null} if none found.
     */
    public String escape(char character, String encoding) {
        if (encoding.startsWith("UTF-")) {
            return switch (character) {
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '&' -> "&amp;";
                case '\'' -> "&#39;";
                default -> null;
            };
        } else if (character < 1000 || (character >= 8000 && character < 10000)) {
            int index = (character < 1000 ? character : character - 7000);
            return characterToEntityReferenceMap[index];
        }
        return null;
    }
}