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

package smile.util;

import java.util.Arrays;

import smile.clustering.CLARANS;
import smile.clustering.KMeans;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.math.rbf.GaussianRadialBasis;
import smile.sort.QuickSort;

/**
 * Some useful functions.
 * 
 * @author Haifeng Li
 */
public class SmileUtils {
    /** Utility classes should not have public constructors. */
    private SmileUtils() {

    }

    /**
     * Sorts each variable and returns the index of values in ascending order.
     * Only numeric attributes will be sorted. Note that the order of original
     * array is NOT altered.
     * 
     * @param x a set of variables to be sorted. Each row is an instance. Each
     * column is a variable.
     * @return the index of values in ascending order
     */
    public static int[][] sort(Attribute[] attributes, double[][] x) {
        int n = x.length;
        int p = x[0].length;
        
        double[] a = new double[n];
        int[][] index = new int[p][];
        
        for (int j = 0; j < p; j++) {
            if (attributes[j].getType() == Attribute.Type.NUMERIC) {
                for (int i = 0; i < n; i++) {
                    a[i] = x[i][j];
                }
                index[j] = QuickSort.sort(a);
            }
        }
        
        return index;        
    }  
}
