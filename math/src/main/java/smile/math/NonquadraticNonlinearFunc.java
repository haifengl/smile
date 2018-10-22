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

/**
 * An interface representing a nonquadratic nonlinear function.
 *
 * @author rayeaster
 */
public interface NonquadraticNonlinearFunc {
    /**
     * Compute the value of the function at x.
     */
    public double f(double x);
    /**
     * Compute the value of the first-order derivative at x.
     */
    public double g(double x);
    /**
     * Compute the value of the second-order derivative at x.
     */
    public double g2(double x);
}
