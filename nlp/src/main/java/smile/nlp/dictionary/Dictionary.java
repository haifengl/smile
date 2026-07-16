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

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A dictionary is a set of words in some natural language.
 *
 * @author Haifeng Li
 */
public interface Dictionary extends Iterable<String> {

    /**
     * Returns true if this dictionary contains the specified word.
     * @param word the query word.
     * @return true if this dictionary contains the specified word.
     */
    boolean contains(String word);

    /**
     * Returns the number of words in this dictionary.
     * @return the number of words in this dictionary.
     */
    int size();

    /**
     * Returns an iterator over the words in this dictionary.
     * @return the iterator.
     */
    Iterator<String> iterator();

    /**
     * Returns a sequential stream of the words in this dictionary.
     * @return a stream of words.
     */
    default Stream<String> stream() {
        Spliterator<String> spliterator = Spliterators.spliterator(
                iterator(), size(), Spliterator.DISTINCT | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }
}
