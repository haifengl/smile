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

package smile.glm;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import smile.data.Default;
import smile.glm.model.*;

/**
 *
 * @author Haifeng Li
 */
public class GLMTest {

    public GLMTest() {
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
    public void testDefault() throws Exception {
        System.out.println("default");

        GLM model = GLM.fit(Default.formula, Default.data, Bernoulli.logit());
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

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}