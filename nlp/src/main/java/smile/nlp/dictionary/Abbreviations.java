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
package smile.nlp.dictionary;

import java.util.Optional;

/**
 * A dictionary interface for abbreviations.
 *
 * @author Haifeng Li
 */
public interface Abbreviations extends Dictionary {

    /**
     * Returns the full word of an abbreviation, or empty if not found.
     * @param abbr the abbreviation.
     * @return the full word of abbreviation, or empty if not found.
     */
    Optional<String> getFull(String abbr);

    /**
     * Returns the abbreviation for a word, or empty if not found.
     * @param word the query word.
     * @return the abbreviation, or empty if not found.
     */
    Optional<String> getAbbreviation(String word);
}
