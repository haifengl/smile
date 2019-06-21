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

package smile.nlp.dictionary;

/**
 * A set of stop words in some language. Stop words is the name given to word
 * s which are filtered out prior to, or after, processing of natural language
 * text. There is no definite list of stop words which all NLP
 * tools incorporate. Not all NLP tools use a stoplist. Some
 * tools specifically avoid using them to support phrase search.
 * <p>
 * Stop words can cause problems when using a search engine to search for
 * phrases that include them, particularly in names such as 'The Who',
 * 'The The', or 'Take That'.
 * 
 * @author Haifeng Li
 */
public interface StopWords extends Dictionary {

}
