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

/**
 * A taxonomy is a tree of terms (concepts) where leaves
 * must be named but intermediary nodes can be anonymous.
 * Concept is a set of synonyms, i.e. group of words that are roughly
 * synonymous in a given context.
 * The distance between two concepts a and b is defined by the length of the
 * path from a to their lowest common ancestor and then to b.
 *
 * @author Haifeng Li
 */
package smile.taxonomy;
