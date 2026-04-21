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
package smile.feature.extraction;

import java.util.List;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.USArrests;
import smile.math.MathEx;
import smile.util.function.TimeFunction;
import smile.tensor.DenseMatrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class GHATest {

    double[][] loadings = {
        {-0.0417043206282872, -0.0448216562696701, -0.0798906594208108, -0.994921731246978},
        {-0.995221281426497, -0.058760027857223, 0.0675697350838043, 0.0389382976351601},
        {-0.0463357461197108, 0.97685747990989, 0.200546287353866, -0.0581691430589319},
        {-0.075155500585547, 0.200718066450337, -0.974080592182491, 0.0723250196376097}
    };
    double[] eigenvalues = {7011.114851, 201.992366, 42.112651, 6.164246};

    public GHATest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        System.out.println("GHA");
        var usa = new USArrests();
        var x = usa.x();
        int k = 3;
        double[] mu = MathEx.colMeans(x);
        DenseMatrix cov = DenseMatrix.of(MathEx.cov(x));
        for (double[] xi : x) {
            MathEx.sub(xi, mu);
        }

        TimeFunction r = TimeFunction.constant(0.00001);
        GHA gha = new GHA(4, k, r);
        for (int iter = 1, t = 0; iter <= 1000; iter++) {
            double error = 0.0;
            for (int i = 0; i < x.length; i++, t++) {
                error += gha.update(x[i]);
            }
            error /= x.length;

            if (iter % 100 == 0) {
                System.out.format("Iter %3d, Error = %.5g%n", iter, error);
            }
        }

        DenseMatrix p = gha.projection;
        DenseMatrix t = p.ata();
        System.out.println(t);

        DenseMatrix s = p.mm(cov).mt(p);
        double[] ev = new double[k];
        System.out.println("Relative error of eigenvalues:");
        for (int i = 0; i < k; i++) {
            ev[i] = Math.abs(eigenvalues[i] - s.get(i, i)) / eigenvalues[i];
            System.out.format("%.4f ", ev[i]);
        }
        System.out.println();

        for (int i = 0; i < k; i++) {
            assertTrue(ev[i] < 0.1);
        }

        double[][] lt = MathEx.transpose(loadings);
        double[] evdot = new double[k];
        double[][] pa = p.toArray(new double[0][] );
        System.out.println("Dot products of learned eigenvectors to true eigenvectors:");
        for (int i = 0; i < k; i++) {
            evdot[i] = MathEx.dot(lt[i], pa[i]);
            System.out.format("%.4f ", evdot[i]);
        }
        System.out.println();

        for (int i = 0; i < k; i++) {
            assertTrue(Math.abs(1.0- Math.abs(evdot[i])) < 0.1);
        }
    }

    @Test
    public void testGivenWrongInputSizeWhenUpdatingGhaThenExceptionIsThrown() {
        // Given
        GHA gha = new GHA(4, 2, TimeFunction.constant(0.001));

        // When / Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gha.update(new double[]{1.0, 2.0, 3.0}));
        assertTrue(ex.getMessage().contains("expected: 4"));
    }

    @Test
    public void testGivenMatrixConstructorWhenUpdatingGhaThenProjectionShapeIsStable() {
        // Given — start from a given weight matrix
        double[][] w = {
                {1.0, 0.0, 0.0, 0.0},
                {0.0, 1.0, 0.0, 0.0}
        };
        GHA gha = new GHA(w, TimeFunction.constant(0.0001));

        // When
        double error = gha.update(new double[]{0.1, 0.2, 0.05, -0.1});

        // Then
        assertEquals(2, gha.projection.nrow());
        assertEquals(4, gha.projection.ncol());
        assertTrue(Double.isFinite(error));
        assertTrue(error >= 0.0);
    }

    @Test
    public void testGivenDataFrameWhenUpdatingGhaThenProjectionIsUpdated() {
        // Given
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType),
                new StructField("c", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{0.1, -0.2, 0.3}),
                Tuple.of(schema, new Object[]{-0.1, 0.2, -0.3})
        ));

        GHA gha = new GHA(3, 2, TimeFunction.constant(0.001), "a", "b", "c");
        int tBefore = gha.t;

        // When
        gha.update(data);

        // Then — iteration counter should have advanced by the number of rows
        assertEquals(tBefore + data.size(), gha.t);
    }

    @Test
    public void testGivenInvalidDimensionsWhenConstructingGhaThenExceptionIsThrown() {
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new GHA(1, 1, TimeFunction.constant(0.001)));
        assertThrows(IllegalArgumentException.class, () -> new GHA(4, 0, TimeFunction.constant(0.001)));
        assertThrows(IllegalArgumentException.class, () -> new GHA(4, 5, TimeFunction.constant(0.001)));
    }
}
