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
import smile.math.Math;

/**
 * The linear dot product kernel. When using a linear kernel, input space is
 * identical to feature space.
 *
 * @author Haifeng Li
 */
public class LinearKernel implements MercerKernel<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public LinearKernel() {
    }

    @Override
    public String toString() {
        return "Linear Kernel";
    }

    @Override
    public double k(double[] x, double[] y) {
        return Math.dot(x, y);
    }
}
