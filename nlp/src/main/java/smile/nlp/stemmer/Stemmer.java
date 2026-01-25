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
package smile.nlp.stemmer;

import java.util.function.Function;

/**
 * A Stemmer transforms a word into its root form. The stemming is a process
 * for removing the commoner morphological and inflexional endings from words
 * (in English). Its main use is as part of a term normalisation process.
 *
 * @author Haifeng Li
 */
public interface Stemmer extends Function<String, String> {
    /**
     * Transforms a word into its root form.
     * @param word the word.
     * @return the stem.
     */
    String stem(String word);

    @Override
    default String apply(String word) {
        return stem(word);
    }
}
