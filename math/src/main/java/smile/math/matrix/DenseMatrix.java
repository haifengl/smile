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
 * An abstract interface of dense matrix.
 *
 * @author Haifeng Li
 */
public interface DenseMatrix extends IMatrix {
    /**
     * Set the entry value at row i and column j.
     */
    public IMatrix set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    public IMatrix update(int i, int j, double x);

    /**
     * A[i][j] += x
     */
    public IMatrix add(int i, int j, double x);

    /**
     * A[i][j] -= x
     */
    public IMatrix sub(int i, int j, double x);

    /**
     * A[i][j] *= x
     */
    public IMatrix mul(int i, int j, double x);

    /**
     * A[i][j] /= x
     */
    public IMatrix div(int i, int j, double x);
}
