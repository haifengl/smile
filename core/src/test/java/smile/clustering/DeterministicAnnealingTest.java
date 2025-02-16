/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DeterministicAnnealingTest {
    
    public DeterministicAnnealingTest() {
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
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();

        var model = DeterministicAnnealing.fit(x, 10, 0.8, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8975, r, 1E-4);
        assertEquals(0.4701, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = model.predict(testx[i]);
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8995, r, 1E-4);
        assertEquals(0.4745, r2, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
