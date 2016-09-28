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
 * An abstract interface of matrix. The most important method is the matrix vector
 * multiplication, which is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations and
 * power iteration and Lanczos algorithm for eigen decomposition, which are
 * usually very efficient for very large and sparse matrices.
 *
 * @author Haifeng Li
 */
public interface IMatrix {
    /**
     * Returns the number of rows.
     */
    public int nrows();

    /**
     * Returns the number of columns.
     */
    public int ncols();

    /**
     * Returns the matrix transpose.
     */
    public IMatrix transpose();

    /**
     * Returns the entry value at row i and column j.
     */
    public double get(int i, int j);

    /**
     * Returns the entry value at row i and column j. For Scala users.
     */
    public double apply(int i, int j);

    /**
     * Returns A' * A
     */
    public IMatrix ata();

    /**
     * Returns A * A'
     */
    public IMatrix aat();

    /**
     * y = A * x
     */
    public void ax(double[] x, double[] y);

    /**
     * y = A * x + y
     */
    public void axpy(double[] x, double[] y);

    /**
     * y = A * x + b * y
     */
    public void axpy(double[] x, double[] y, double b);

    /**
     * y = A' * x
     */
    public void atx(double[] x, double[] y);

    /**
     * y = A' * x + y
     */
    public void atxpy(double[] x, double[] y);

    /**
     * y = A' * x + b * y
     */
    public void atxpy(double[] x, double[] y, double b);

    /**
     * Solve A<sub>d</sub> * x = b for the preconditioner matrix A<sub>d</sub>.
     * The preconditioner matrix A<sub>d</sub> is close to A and should be
     * easy to solve for linear systems. This method is useful for preconditioned 
     * conjugate gradient method. The preconditioner matrix could be as simple
     * as the trivial diagonal part of A in some cases.
     */
    public void asolve(double[] b, double[] x);
}
