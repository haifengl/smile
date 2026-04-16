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
package smile.nlp.pos;

import java.util.Optional;
import java.util.regex.Pattern;
import smile.util.Regex;

/**
 * Part-of-speech tagging by regular expression.
 *
 * @author Haifeng Li
 */
class RegexPOSTagger {
    /** Utility classes should not have public constructors. */
    private RegexPOSTagger() {

    }

    /**
     * Tagging words based on regular expressions over word strings.
     */
    private static final Pattern[] REGEX = {
            Regex.CARDINAL_NUMBER,
            Regex.CARDINAL_NUMBER_WITH_COMMA,
            Regex.PHONE_NUMBER,
            Regex.PHONE_NUMBER_EXTENSION,
            Regex.URL,
            Regex.EMAIL_ADDRESS
    };

    /**
     * POS tags for words matching regular expressions.
     */
    private static final PennTreebankPOS[] REGEX_POS = {
            PennTreebankPOS.CD,
            PennTreebankPOS.CD,
            PennTreebankPOS.NN,
            PennTreebankPOS.NN,
            PennTreebankPOS.NN,
            PennTreebankPOS.NN
    };

    /**
     * Returns the POS tag of a word based on regular-expression pattern
     * matching, or an empty {@link Optional} if no pattern matches.
     *
     * @param word the word to tag.
     * @return an {@code Optional} containing the matched {@link PennTreebankPOS},
     *         or empty if the word does not match any pattern.
     */
    public static Optional<PennTreebankPOS> tag(String word) {
        for (int i = 0; i < REGEX.length; i++) {
            if (REGEX[i].matcher(word).matches()) {
                return Optional.of(REGEX_POS[i]);
            }
        }
        return Optional.empty();
    }
}
