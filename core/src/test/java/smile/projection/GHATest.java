/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.projection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.data.USArrests;
import smile.math.MathEx;
import smile.math.TimeFunction;
import smile.math.matrix.Matrix;

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

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test() {
        System.out.println("GHA");

        int k = 3;
        double[] mu = MathEx.colMeans(USArrests.x);
        Matrix cov = new Matrix(MathEx.cov(USArrests.x));
        for (int i = 0; i < USArrests.x.length; i++) {
           MathEx.sub(USArrests.x[i], mu);
        }

        TimeFunction r = TimeFunction.constant(0.00001);
        GHA gha = new GHA(4, k, r);
        for (int iter = 1, t = 0; iter <= 1000; iter++) {
            double error = 0.0;
            for (int i = 0; i < USArrests.x.length; i++, t++) {
                error += gha.update(USArrests.x[i]);
            }
            error /= USArrests.x.length;

            if (iter % 100 == 0) {
                System.out.format("Iter %3d, Error = %.5g%n", iter, error);
            }
        }

        Matrix p = gha.projection();
        Matrix t = p.ata();
        System.out.println(t);

        Matrix s = p.mm(cov).mt(p);
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
        double[][] pa = p.toArray();
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
}
