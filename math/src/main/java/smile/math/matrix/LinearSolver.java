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

package smile.math.matrix;

/**
 * The interface of the solver of linear system.
 *
 * @author Haifeng Li
 */
public interface LinearSolver {
    /**
     * Solve A*x = b.
     * @param b   a vector with as many rows as A.
     * @param x   is output vector so that A*x = b
     * @return the solution vector x
     * @throws RuntimeException if matrix is singular.
     */
    public double[] solve(double[] b, double[] x);
}
