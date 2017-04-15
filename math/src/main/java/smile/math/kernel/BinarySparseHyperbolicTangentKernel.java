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

import java.lang.Math;

/**
 * The hyperbolic tangent kernel.
 * k(u, v) = tanh(&gamma; u<sup>T</sup>v - &lambda;), where &gamma; is the scale
 * of the used inner product and &lambda; is the offset of the used inner
 * product. If the offset is negative the likelihood of obtaining a kernel
 * matrix that is not positive definite is much higher (since then even some
 * diagonal elements may be negative), hence if this kernel has to be used,
 * the offset should always be positive. Note, however, that this is no
 * guarantee that the kernel will be positive.
 * <p>
 * The hyperbolic tangent kernel was quite popular for support vector machines
 * due to its origin from neural networks. However, it should be used carefully
 * since the kernel matrix may not be positive semi-definite. Besides, it was
 * reported the hyperbolic tangent kernel is not better than the Gaussian kernel
 * in general.
 * <p>
 * The kernel works sparse binary array as int[], which are the indices of
 * nonzero elements.
 *
 * @author Haifeng Li
 */
public class BinarySparseHyperbolicTangentKernel implements MercerKernel<int[]> {

    private double scale;
    private double offset;

    /**
     * Constructor with scale 1.0 and offset 0.0.
     */
    public BinarySparseHyperbolicTangentKernel() {
        this(1, 0);
    }

    /**
     * Constructor.
     */
    public BinarySparseHyperbolicTangentKernel(double scale, double offset) {
        this.scale = scale;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return String.format("Sparse Binary Hyperbolic Tangent Kernel (scale = %.4f, offset = %.4f)", scale, offset);
    }

    @Override
    public double k(int[] x, int[] y) {
        double dot = 0.0;

        for (int p1 = 0, p2 = 0; p1 < x.length && p2 < y.length; ) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                dot++;
                p1++;
                p2++;
            } else if (i1 > i2) {
                p2++;
            } else {
                p1++;
            }
        }

        return Math.tanh(scale * dot + offset);
    }
}
