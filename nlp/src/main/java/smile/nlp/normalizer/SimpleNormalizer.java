/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.nlp.normalizer;

import java.util.regex.Pattern;

/**
 * A baseline normalizer for processing Unicode text:
 * <ul>
 * <li>Apply Unicode normalization form NFKC.</li>
 * <li>Strip, trim, normalize, and compress whitespace.</li>
 * <li>Remove control and formatting characters.</li>
 * <li>Normalize greater-than and less-than signs, double and single quotes, and dashes.</li>
 * </ul>
 *
 * @author Mark Arehart
 */
public class SimpleNormalizer implements Normalizer {

    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private static final Pattern CONTROL_FORMAT_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final Pattern LT_SIGNS = Pattern.compile("[\\u003C\\u2039\\u2329\\u276E\\u3008]");

    private static final Pattern GT_SIGNS = Pattern.compile("[\\u003E\\u203A\\u232A\\u276F\\u3009]");

    private static final Pattern DOUBLE_QUOTES = Pattern.compile("\\u0022\\u02BA\\u201C\\u201D\\u201E\\u201F\\u2033\\u2036\\u275D\\u275E\\u301D\\u301E\\u301F\\uFF02");

    private static final Pattern SINGLE_QUOTES = Pattern.compile("[\\u0060\\u0027\\u02BB\\u02BC\\u02BD\\u2018\\u2019\\u201A\\u201B\\u275B\\u275C]");

    private static final Pattern DASHES = Pattern.compile("[\\u002D\\u2012\\u2013\\u2014\\u2015\\uFE58\\uFE31\\uFE32]");

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
        text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        text = WHITESPACE.matcher(text).replaceAll(" ");
        text = CONTROL_FORMAT_CHARS.matcher(text).replaceAll("");
        text = LT_SIGNS.matcher(text).replaceAll("<");
        text = GT_SIGNS.matcher(text).replaceAll(">");
        text = DOUBLE_QUOTES.matcher(text).replaceAll("\"");
        text = SINGLE_QUOTES.matcher(text).replaceAll("'");
        text = DASHES.matcher(text).replaceAll("-");
        return text;
    }
}
