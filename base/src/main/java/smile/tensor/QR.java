/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import static smile.linalg.lapack.clapack_h.*;
import static smile.linalg.Diag.*;
import static smile.linalg.Side.*;
import static smile.linalg.Transpose.*;
import static smile.linalg.UPLO.*;

/**
 * The QR decomposition. For an m-by-n matrix A with {@code m >= n},
 * the QR decomposition is an m-by-n orthogonal matrix Q and
 * an n-by-n upper triangular matrix R such that A = Q*R.
 * <p>
 * The QR decomposition always exists, even if the matrix does not have
 * full rank. The primary use of the QR decomposition is in the least squares
 * solution of non-square systems of simultaneous linear equations.
 *
 * @param qr the QR decomposition.
 * @param tau the scalar factors of the elementary reflectors.
 *
 * @author Haifeng Li
 */
public record QR(DenseMatrix qr, Vector tau) implements Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(QR.class);

    /**
     * Returns the Cholesky decomposition of A'A.
     * @return the Cholesky decomposition of A'A.
     */
    public Cholesky toCholesky() {
        int n = qr.n;
        DenseMatrix L = qr.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                L.set(i, j, qr.get(j, i));
            }
        }

        L.withUplo(LOWER);
        return new Cholesky(L);
    }

    /**
     * Returns the upper triangular factor.
     * @return the upper triangular factor.
     */
    public DenseMatrix R() {
        int n = qr.n;
        DenseMatrix R = tau.diagflat();
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                R.set(i, j, qr.get(i, j));
            }
        }

        return R;
    }

    /**
     * Returns the orthogonal factor.
     * @return the orthogonal factor.
     */
    public DenseMatrix Q() {
        DenseMatrix Q = qr.copy();
        Vector work = Q.vector(qr.n);
        int[] m = { qr.m };
        int[] n = { qr.n };
        int[] k = { Math.min(qr.m, qr.n) };
        int[] lda = { Q.ld };
        int[] lwork = { work.size() };
        int[] info = { 0 };
        MemorySegment m_ = MemorySegment.ofArray(m);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment k_ = MemorySegment.ofArray(k);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment lwork_ = MemorySegment.ofArray(lwork);
        MemorySegment info_ = MemorySegment.ofArray(info);
        switch(Q.scalarType()) {
            case Float64 -> dorgqr_(m_, n_, k_, Q.memory, lda_, tau.memory, work.memory, lwork_, info_);
            case Float32 -> sorgqr_(m_, n_, k_, Q.memory, lda_, tau.memory, work.memory, lwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + Q.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK ORGRQ error code: {}", info[0]);
            throw new ArithmeticException("LAPACK ORGRQ error code: " + info[0]);
        }
        return Q;
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param b the right hand side of overdetermined linear system.
     * @throws RuntimeException when the matrix is rank deficient.
     * @return the solution vector.
     */
    public Vector solve(double[] b) {
        // Don't call vector(b) as it will be overwritten.
        Vector x = qr.vector(qr.m);
        for (int i = 0; i < qr.m; i++) x.set(i, b[i]);
        solve(x);
        return x.slice(0, qr.n);
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param b the right hand side of overdetermined linear system.
     * @throws RuntimeException when the matrix is rank deficient.
     * @return the solution vector.
     */
    public Vector solve(float[] b) {
        // Don't call vector(b) as it will be overwritten.
        Vector x = qr.vector(qr.m);
        for (int i = 0; i < qr.m; i++) x.set(i, b[i]);
        solve(x);
        return x.slice(0, qr.n);
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param B the right hand side of overdetermined linear system.
     *          B will be overwritten with the solution matrix on output.
     * @throws RuntimeException when the matrix is rank deficient.
     */
    public void solve(DenseMatrix B) {
        if (qr.scalarType() != B.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + B.scalarType() + " != " + qr.scalarType());
        }
        if (qr.m != B.m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrow(), qr.nrow(), B.nrow(), B.ncol()));
        }

        Vector work = qr.vector(1);
        byte[] side = { LEFT.lapack() };
        byte[] trans = { TRANSPOSE.lapack() };
        byte[] unit = { NON_UNIT.lapack() };
        int[] m = { B.m };
        int[] n = { B.n };
        int[] k = { Math.min(qr.m, qr.n) };
        int[] nrhs = { B.n };
        int[] lda = { qr.ld };
        int[] ldb = { B.ld };
        int[] lwork = { -1 };
        int[] info = { 0 };
        MemorySegment side_ = MemorySegment.ofArray(side);
        MemorySegment trans_ = MemorySegment.ofArray(trans);
        MemorySegment unit_ = MemorySegment.ofArray(unit);
        MemorySegment m_ = MemorySegment.ofArray(m);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment k_ = MemorySegment.ofArray(k);
        MemorySegment nrhs_ = MemorySegment.ofArray(nrhs);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment ldb_ = MemorySegment.ofArray(ldb);
        MemorySegment lwork_ = MemorySegment.ofArray(lwork);
        MemorySegment info_ = MemorySegment.ofArray(info);

        // query workspace size for ORMQR
        switch(qr.scalarType()) {
            case Float64 -> dormqr_(side_, trans_, m_, n_, k_, qr.memory, lda_, tau.memory, B.memory, ldb_, work.memory, lwork_, info_);
            case Float32 -> sormqr_(side_, trans_, m_, n_, k_, qr.memory, lda_, tau.memory, B.memory, ldb_, work.memory, lwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + qr.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK ORMQR error code: {}", info[0]);
            throw new IllegalArgumentException("LAPACK ORMQR error code: " + info[0]);
        }

        // execute ORMQR
        work = qr.vector((int) work.get(0));
        lwork[0] = work.size();
        switch(qr.scalarType()) {
            case Float64 -> dormqr_(side_, trans_, m_, n_, k_, qr.memory, lda_, tau.memory, B.memory, ldb_, work.memory, lwork_, info_);
            case Float32 -> sormqr_(side_, trans_, m_, n_, k_, qr.memory, lda_, tau.memory, B.memory, ldb_, work.memory, lwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + qr.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK ORMQR error code: {}", info[0]);
            throw new IllegalArgumentException("LAPACK ORMQR error code: " + info[0]);
        }

        side[0] = UPPER.lapack();
        trans[0] = NO_TRANSPOSE.lapack();
        m[0] = qr.m;
        n[0] = qr.n;
        switch(qr.scalarType()) {
            case Float64 -> dtrtrs_(side_, trans_, unit_, n_, nrhs_, qr.memory, lda_, B.memory, ldb_, info_);
            case Float32 -> strtrs_(side_, trans_, unit_, n_, nrhs_, qr.memory, lda_, B.memory, ldb_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + qr.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK TRTRS error code: {}", info[0]);
            throw new IllegalArgumentException("LAPACK TRTRS error code: " + info[0]);
        }
    }
}
