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
 * The preconditioner matrix in the biconjugate gradient method.
 *
 * @author Haifeng Li
 */
public interface Preconditioner {
    /**
     * Solve A<sub>d</sub> * x = b for the preconditioner matrix A<sub>d</sub>.
     * The preconditioner matrix A<sub>d</sub> is close to A and should be
     * easy to solve for linear systems. This method is useful for preconditioned
     * conjugate gradient method. The preconditioner matrix could be as simple
     * as the trivial diagonal part of A in some cases.
     */
    public void asolve(double[] b, double[] x);
}
