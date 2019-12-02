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

package smile.math.distance;

import java.util.HashSet;
import java.util.Set;

/**
 * The Jaccard index, also known as the Jaccard similarity coefficient is a
 * statistic used for comparing the similarity and diversity of sample sets.
 *
 * The Jaccard coefficient measures similarity between sample sets, and is
 * defined as the size of the intersection divided by the size of the union
 * of the sample sets.
 *
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
        Set<T> union = new HashSet<>();
        Set<T> intersection = new HashSet<>();

        for (int i = 0; i < b.length; i++)
            union.add(b[i]);

        for (int i = 0; i < a.length; i++)
            intersection.add(a[i]);

        intersection.retainAll(union);

        for (int i = 0; i < a.length; i++)
            union.add(a[i]);

        return 1.0 - (double) intersection.size() / union.size();
    }

    /**
     * Returns the Jaccard distance between sets.
     */
    public static <T> double d(Set<T> a, Set<T> b) {
        Set<T> union = new HashSet<>(a);
        union.addAll(b);

        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        return 1.0 - (double) intersection.size() / union.size();
    }
}
