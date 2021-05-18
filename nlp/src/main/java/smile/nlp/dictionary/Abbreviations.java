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

package smile.nlp.dictionary;

/**
 * A dictionary interface for abbreviations.
 *
 * @author Haifeng Li
 */
public interface Abbreviations extends Dictionary {

    /**
     * Returns the full word of an abbreviation. If the abbreviation
     * doesn't exist in the dictionary return null.
     * @param abbr the abbreviation.
     * @return the full word of abbreviation or null.
     */
    String getFull(String abbr);

    /**
     * Returns the abbreviation for a word. If the word doesn't exist in the
     * dictionary return null.
     * @param word the query word.
     * @return the abbreviation or null.
     */
    String getAbbreviation(String word);
}
