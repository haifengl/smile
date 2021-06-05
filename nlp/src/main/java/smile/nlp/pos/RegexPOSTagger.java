/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.nlp.pos;

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
     * Returns the POS tag of a given word based on the regular expression over
     * word string. Returns null if no match.
     */
    public static PennTreebankPOS tag(String word) {
        for (int i = 0; i < REGEX.length; i++) {
            if (REGEX[i].matcher(word).matches()) {
                return REGEX_POS[i];
            }
        }

        return null;
    }
}
