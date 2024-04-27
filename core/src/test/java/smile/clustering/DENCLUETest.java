/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.clustering;

import smile.io.Read;
import smile.io.Write;
import smile.test.data.GaussianMixture;
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DENCLUETest {
    
    public DENCLUETest() {
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
    public void testGaussianMixture() throws Exception {
        System.out.println("Gaussian Mixture");

        double[][] x = GaussianMixture.x;
        int[] y = GaussianMixture.y;

        MathEx.setSeed(19650218); // to get repeatable results.
        DENCLUE model = DENCLUE.fit(x, 0.85, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.y);
        double r2 = AdjustedRandIndex.of(y, model.y);
        System.out.println("The number of clusters: " + model.k);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.6080, r, 1E-4);
        assertEquals(0.2460, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.y));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.y));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.y));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.y));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.y));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.y));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
