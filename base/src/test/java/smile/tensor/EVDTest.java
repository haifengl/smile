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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import smile.linalg.NativeLibrary;
import static org.junit.jupiter.api.Assertions.*;
import static smile.linalg.UPLO.*;

/**
 * Unit tests for {@link EVD} in both default and pure-Java backend modes.
 *
 * @author Haifeng Li
 */
public class EVDTest {
    static final double[][] SYMM = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
    };
    static final double[] SYMM_EVALS = {1.7498382, 0.3165784, 0.1335834};

    static final double[][] ASYMM = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.8000, 0.3000, 0.8000}
    };
    static final double[] ASYMM_EVALS = {1.79171122, 0.31908143, 0.08920735};

    @AfterEach
    public void tearDown() {
        System.clearProperty(NativeLibrary.BACKEND_PROPERTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSymmetricEVD(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(SYMM).withUplo(LOWER);

        // When
        EVD evd = a.copy().eigen().sort();
        DenseMatrix V = evd.Vr();
        DenseMatrix D = evd.diag();

        // Then
        assertArrayEquals(SYMM_EVALS, evd.wr().toArray(new double[0]), 1E-7);
        // A * V = V * D
        assertEquals(DenseMatrix.of(SYMM).mm(V), V.mm(D));
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testAsymmetricRealEVDWithLeftAndRightVectors(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(ASYMM);

        // When
        EVD evd = a.copy().eigen(true, true).sort();
        DenseMatrix Vr = evd.Vr();
        DenseMatrix Vl = evd.Vl();
        DenseMatrix D = evd.diag();

        // Then
        assertArrayEquals(ASYMM_EVALS, evd.wr().toArray(new double[0]), 1E-7);
        // Right eigenvectors: A * Vr = Vr * D
        assertEquals(a.mm(Vr), Vr.mm(D));
        // Left eigenvectors: A' * Vl = Vl * D
        assertEquals(a.transpose().mm(Vl), Vl.mm(D));
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testComplexConjugateEigenvalues(String backend) {
        // Given: rotation-scaling matrix with eigenvalues 1 ± 2i and 3
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] rot = {
                { 1.0, -2.0, 0.0},
                { 2.0,  1.0, 0.0},
                { 0.0,  0.0, 3.0}
        };
        DenseMatrix a = DenseMatrix.of(rot);

        // When
        EVD evd = a.copy().eigen(false, true).sort();
        DenseMatrix Vr = evd.Vr();
        DenseMatrix D = evd.diag();

        // Then: sorted by magnitude squared: 3^2=9, |1±2i|^2=5
        assertEquals(3.0, evd.wr().get(0), 1E-7);
        assertEquals(0.0, evd.wi().get(0), 1E-7);
        assertEquals(1.0, evd.wr().get(1), 1E-7);
        assertEquals(1.0, evd.wr().get(2), 1E-7);
        assertEquals(2.0, Math.abs(evd.wi().get(1)), 1E-7);
        assertEquals(-evd.wi().get(1), evd.wi().get(2), 1E-7);
        // Block-diagonal relation: A * Vr = Vr * D
        assertEquals(a.mm(Vr), Vr.mm(D));
    }
}
