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

package smile.util;

import smile.math.MathEx;
import smile.sort.QuickSort;

import java.util.Arrays;

/**
 * Some useful functions.
 * 
 * @author Haifeng Li
 */
public interface SmileUtils {
    /**
     * Sorts each variable and returns the index of values in ascending order.
     * 
     * @param x a set of variables to be sorted. Each row is an instance. Each
     * column is a variable.
     * @return the index of values in ascending order
     */
    default int[][] sort(double[][] x) {
        int n = x.length;
        int p = x[0].length;
        
        double[] a = new double[n];
        int[][] index = new int[p][];
        
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                a[i] = x[i][j];
            }
            index[j] = QuickSort.sort(a);
        }
        
        return index;        
    }

    /**
     * Returns the sorted unique class labels.
     *
     * @param y the class labels of instances.
     */
    default int[] labels(int[] y) {
        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);

        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]);
            }

            if (labels[i] != i) {
                throw new IllegalArgumentException("Missing class: " + i);
            }
        }

        return labels;
    }
}
