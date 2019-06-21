/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.nlp.normalizer;

import java.util.regex.Pattern;

/**
 * A baseline normalizer for processing Unicode text:
 * <ul>
 * <li>Apply Unicode normalization form NFKC.</li>
 * <li>Strip, trim, normalize, and compress whitespace.</li>
 * <li>Remove control and formatting characters.</li>
 * <li>Normalize dash, double and single quotes.</li>
 * </ul>
 *
 * @author Mark Arehart
 */
public class SimpleNormalizer implements Normalizer {

    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private static final Pattern CONTROL_FORMAT_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final Pattern DOUBLE_QUOTES = Pattern.compile("[\\u02BA\\u201C\\u201D\\u201E\\u201F\\u2033\\u2036\\u275D\\u275E\\u301D\\u301E\\u301F\\uFF02]");

    private static final Pattern SINGLE_QUOTES = Pattern.compile("[\\u0060\\u02BB\\u02BC\\u02BD\\u2018\\u2019\\u201A\\u201B\\u275B\\u275C]");

    private static final Pattern DASH = Pattern.compile("[\\u2012\\u2013\\u2014\\u2015\\u2053]");

    /**
     * The singleton instance.
     */
    private static SimpleNormalizer singleton = new SimpleNormalizer();

    /**
     * Constructor.
     */
    private SimpleNormalizer() { }

    /**
     * Returns the singleton instance.
     */
    public static SimpleNormalizer getInstance() {
        return singleton;
    }

    @Override
    public String normalize(String text) {

        text = text.trim();

        if (!java.text.Normalizer.isNormalized(text, java.text.Normalizer.Form.NFKC)) {
            text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        }

        text = WHITESPACE.matcher(text).replaceAll(" ");

        text = CONTROL_FORMAT_CHARS.matcher(text).replaceAll("");

        text = DOUBLE_QUOTES.matcher(text).replaceAll("\"");

        text = SINGLE_QUOTES.matcher(text).replaceAll("'");

        text = DASH.matcher(text).replaceAll("--");

        return text;
    }
}
