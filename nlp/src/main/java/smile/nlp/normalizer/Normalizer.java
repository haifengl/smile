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

package smile.nlp.normalizer;

/**
 * Normalization transforms text into a canonical form by removing unwanted
 * variations. Normalization may range from light textual cleanup such as
 * compressing whitespace to more aggressive and knowledge-intensive forms
 * like standardizing date formats or expanding abbreviations. The nature and
 * extent of normalization, as well as whether it is most appropriate to apply
 * on the document, sentence, or token level, must be determined in the context
 * of a specific application.
 *
 * @author Mark Arehart
 */
public interface Normalizer {

    /**
     * Normalize the given string.
     */
    String normalize(String text);
}
