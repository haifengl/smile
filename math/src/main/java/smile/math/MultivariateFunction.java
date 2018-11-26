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

package smile.math;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;

/**
 * An interface representing a multivariate real function.
 *
 * @author Haifeng Li
 */
public interface MultivariateFunction extends ToDoubleFunction<double[]>, Serializable {
    /**
     * Computes the value of the function at x.
     */
    double f(double[] x);

    /**
     * Computes the value of the function at x.
     */
    default double applyAsDouble(double[] x) {
        return f(x);
    }

    /**
     * Computes the value of the function at x.
     * It delegates the computation to f().
     * This is simply for Scala convenience.
     */
    default double apply(double... x) {
        return f(x);
    }
}
