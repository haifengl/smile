/*******************************************************************************
 * Copyright (c) 2015 Diego Catalano
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
import java.io.Serializable;

/**
 * The Hellinger Mercer Kernel.

 * @author Diego Catalano
 */
public class HellingerKernel implements MercerKernel<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructor.
     */
    public HellingerKernel() {}

    @Override
    public String toString() {
        return "Hellinger Kernel";
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += Math.sqrt(x[i] * y[i]);
        }

        return sum;
    }
}