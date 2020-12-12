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

package smile.math.distance;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The Jaccard index, also known as the Jaccard similarity coefficient is a
 * statistic used for comparing the similarity and diversity of sample sets.
 * <p>
 * The Jaccard coefficient measures similarity between sample sets, and is
 * defined as the size of the intersection divided by the size of the union
 * of the sample sets.
 * <p>
 * The Jaccard distance, which measures dissimilarity between sample sets,
 * is complementary to the Jaccard coefficient and is obtained by subtracting
 * the Jaccard coefficient from 1, or, equivalently, by dividing the difference
 * of the sizes of the union and the intersection of two sets by the size of
 * the union.
 *
 * @author Haifeng Li
 */
public class JaccardDistance<T> implements Distance<T[]> {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public JaccardDistance() {
    }

    @Override
    public String toString() {
        return "Jaccard Distance";
    }

    @Override
    public double d(T[] a, T[] b) {
        Set<T> union = new HashSet<>(Arrays.asList(b));
        Collections.addAll(union, a);

        Set<T> intersection = new HashSet<>();
        Collections.addAll(intersection, a);
        intersection.retainAll(union);

        return 1.0 - (double) intersection.size() / union.size();
    }

    /**
     * Returns the Jaccard distance between sets.
     * @param a a vector.
     * @param b a vector.
     * @param <T> the data type of set elements.
     * @return the distance.
     */
    public static <T> double d(Set<T> a, Set<T> b) {
        Set<T> union = new HashSet<>(a);
        union.addAll(b);

        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        return 1.0 - (double) intersection.size() / union.size();
    }
}
