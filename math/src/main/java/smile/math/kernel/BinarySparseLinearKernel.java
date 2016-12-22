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

package smile.math.kernel;

import java.io.Serializable;

/**
 * The linear dot product kernel on sparse binary arrays in int[],
 * which are the indices of nonzero elements.
 * When using a linear kernel, input space is identical to feature space.
 *
 * @author Haifeng Li
 */
public class BinarySparseLinearKernel implements MercerKernel<int[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public BinarySparseLinearKernel() {
    }

    @Override
    public String toString() {
        return "Sparse Binary Linear Kernel";
    }

    @Override
    public double k(int[] x, int[] y) {
        int s = 0;
        for (int p1 = 0, p2 = 0; p1 < x.length && p2 < y.length; ) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                s++;
                p1++;
                p2++;
            } else if (i1 > i2) {
                p2++;
            } else {
                p1++;
            }
        }
        return s;
    }
}
