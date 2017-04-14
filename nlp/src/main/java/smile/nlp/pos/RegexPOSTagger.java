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

package smile.nlp.pos;

import java.util.regex.Pattern;

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
        // cardinal numbers
        Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$"),
        // cardinal numbers, optionally thousands are separated by comma
        Pattern.compile("^\\d{1,3},(\\d{3},)*\\d{3}(\\.\\d+)?$"),
        // U.S. phone number
        Pattern.compile("^(((\\d{3})|(\\d{3}-)){0,1}\\d{3}-\\d{4})((x|ext)\\d{1,5}){0,1}$"),
        // U.S. phone number extension
        Pattern.compile("^(x|ext)\\d{1,5}$"),
        // internet URLs
        Pattern.compile("^((mailto\\:|(news|(ht|f)tp(s?))\\://){1}\\S+)$"),
        // email address
        Pattern.compile("^(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])$"),
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
