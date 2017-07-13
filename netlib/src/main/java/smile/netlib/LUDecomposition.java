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

package smile.netlib;

import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;
import smile.math.matrix.ColumnMajorMatrix;
import smile.math.matrix.LU;

/**
 * For an m-by-n matrix A with m &ge; n, the LU decomposition is an m-by-n
 * unit lower triangular matrix L, an n-by-n upper triangular matrix U,
 * and a permutation vector piv of length m so that A(piv,:) = L*U.
 * If m &lt; n, then L is m-by-m and U is m-by-n.
 * <p>
 * The LU decomposition with pivoting always exists, even if the matrix is
 * singular. The primary use of the LU decomposition is in the solution of
 * square systems of simultaneous linear equations if it is not singular.
 * <p>
 * This decomposition can also be used to calculate the determinant.
 *
 * @author Haifeng Li
 */
public class LUDecomposition extends smile.math.matrix.LUDecomposition {
    /** Constructor */
    public LUDecomposition() {

    }

    /**
     * LU decompsition. The decomposition will be stored in the input matrix.
     * @param  A   the matrix to be decomposed
     */
    @Override
    public LU decompose(DenseMatrix A) {
        boolean singular = false;

        int[] piv = new int[Math.min(A.nrows(), A.ncols())];
        intW info = new intW(0);
        LAPACK.getInstance().dgetrf(A.nrows(), A.ncols(), A.data(), A.ld(), piv, info);

        if (info.val > 0)
            singular = true;
        else if (info.val < 0)
            throw new IllegalArgumentException("LAPACK DGETRF error code: " + info.val);

        return new LU(A, piv, singular);
    }
}
