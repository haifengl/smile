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
