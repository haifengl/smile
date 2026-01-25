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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp.tokenizer;

import java.util.function.Function;

/**
 * A token is a string of characters, categorized according to the rules as a
 * symbol. The process of forming tokens from an input stream of characters
 * is called tokenization.
 * <p>
 * This is not as easy as it sounds. For example, when should a token containing
 * a hypen be split into two or more tokens? When does a period indicate the
 * end of an abbreviation as opposed to a sentence or a number or a
 * Roman numeral? Sometimes a period can act as a sentence terminator and
 * an abbreviation terminator at the same time. When should a single quote be
 * split from a word?
 *
 * @author Haifeng Li
 */
public interface Tokenizer extends Function<String, String[]> {
    /**
     * Splits the string into a list of tokens.
     * @param text the text.
     * @return the tokens.
     */
    String[] split(String text);

    @Override
    default String[] apply(String text) {
        return split(text);
    }
}
