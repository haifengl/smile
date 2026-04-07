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
package smile.glm;

import smile.glm.model.*;
import smile.io.Read;
import smile.io.Write;
import smile.datasets.Default;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GLMTest {

    public GLMTest() {
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
    public void testDefault() throws Exception {
        System.out.println("default");

        var dataset = new Default();
        GLM model = GLM.fit(dataset.formula(), dataset.data(), Bernoulli.logit());
        System.out.println(model);

        assertEquals(1571.5448, model.deviance(), 1E-4);
        assertEquals(-785.7724, model.logLikelihood(), 1E-4);
        assertEquals(1579.5448, model.AIC(), 1E-4);
        assertEquals(1608.3862, model.BIC(), 1E-4);

        double[][] ztest = {
                {-10.869045, 4.923e-01, -22.0793,   0.00000},
                {-6.468e-01, 2.363e-01,  -2.7376,   0.00619},
                { 5.737e-03, 2.319e-04,  24.7365,   0.00000},
                { 3.033e-06, 8.203e-06,   0.3698,   0.71153}
        };

        for (int i = 0; i < ztest.length; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(ztest[i][j], model.ztest()[i][j], 1E-4);
            }
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    /**
     * Tests that Poisson deviance and nullDeviance are finite when the
     * response vector contains zero counts (the zero-count bug fix).
     * When y_i = 0, the deviance contribution should be 2 * mu_i (not NaN).
     */
    @Test
    public void testPoissonZeroCounts() {
        System.out.println("Poisson zero-count deviance");

        // y = [0, 1, 2, 3, 4, 5] with mu = [0.5, 1.0, 2.0, 3.0, 4.0, 5.0]
        double[] y  = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] mu = {0.5, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] residuals = new double[y.length];

        var model = Poisson.log();

        double dev = model.deviance(y, mu, residuals);
        assertTrue(Double.isFinite(dev),
                "Poisson deviance must be finite when y contains zeros, got " + dev);
        assertTrue(dev >= 0.0,
                "Poisson deviance must be non-negative, got " + dev);

        // y[0]=0, mu[0]=0.5 → contribution = 2*(0 - 0 + 0.5) = 1.0
        assertEquals(1.0, 2.0 * mu[0], 1E-10);

        // residual for y=0 should be negative (y < mu)
        assertTrue(residuals[0] <= 0.0,
                "Deviance residual for y=0 should be <= 0, got " + residuals[0]);

        // All residuals must be finite
        for (int i = 0; i < residuals.length; i++) {
            assertTrue(Double.isFinite(residuals[i]),
                    "Deviance residual[" + i + "] must be finite, got " + residuals[i]);
        }

        double nullDev = model.nullDeviance(y, 2.5);  // mean of y
        assertTrue(Double.isFinite(nullDev),
                "Poisson null deviance must be finite when y contains zeros, got " + nullDev);
        assertTrue(nullDev >= 0.0,
                "Poisson null deviance must be non-negative, got " + nullDev);
    }
}