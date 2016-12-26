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
import smile.math.SparseArray;
import smile.math.Math;

/**
 * The Gaussian Mercer Kernel. k(u, v) = e<sup>-||u-v||<sup>2</sup> / (2 * &sigma;<sup>2</sup>)</sup>,
 * where &sigma; &gt; 0 is the scale parameter of the kernel.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being over used.

 * @author Haifeng Li
 */
public class SparseGaussianKernel implements MercerKernel<SparseArray>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The width of the kernel.
     */
    private double gamma;
    
    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     */
    public SparseGaussianKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.gamma = 0.5 / (sigma * sigma);
    }

    @Override
    public String toString() {
        return String.format("Sparse Gaussian Kernel (\u02E0 = %.4f)", Math.sqrt(0.5/gamma));
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        return Math.exp(-gamma * Math.squaredDistance(x, y));
    }
}
