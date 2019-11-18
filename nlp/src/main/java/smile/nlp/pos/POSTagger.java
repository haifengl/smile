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

package smile.nlp.pos;

/**
 * Part-of-speech tagging (POS tagging) is the process of marking up the words
 * in a sentence as corresponding to a particular part of speech. Part-of-speech
 * tagging is hard because some words can represent more than one part of speech
 * at different times, and because some parts of speech are complex or unspoken.
 *
 * @author Haifeng Li
 */
public interface POSTagger {

    /**
     * Tags the sentence in the form of a sequence of words
     */
    PennTreebankPOS[] tag(String[] sentence);
}
