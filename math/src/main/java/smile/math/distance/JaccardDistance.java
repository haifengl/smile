/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.distance;

import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;

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
public class JaccardDistance<T> implements Distance<T[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public JaccardDistance() {
    }

    @Override
    public String toString() {
        return "Jaccard distance";
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
