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

import smile.data.SparseDataset;
import smile.data.SampleInstance;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.util.SparseArray;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class SIBTest {
    
    public SIBTest() {
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
    public void testParseNG20() throws Exception {
        System.out.println("NG20");

        MathEx.setSeed(19650218); // to get repeatable results.

        SparseDataset<Integer> train = Read.libsvm(smile.io.Paths.getTestData("libsvm/news20.dat"));
        SparseDataset<Integer> test = Read.libsvm(smile.io.Paths.getTestData("libsvm/news20.t.dat"));

        SparseArray[] trainx = train.stream().map(SampleInstance::x).toArray(SparseArray[]::new);
        int[] y = train.stream().mapToInt(SampleInstance::y).toArray();
        int[] testy = test.stream().mapToInt(SampleInstance::y).toArray();
            
        SIB model = SIB.fit(trainx, 20);
        System.out.println(model);

        double r = RandIndex.of(y, model.y);
        double r2 = AdjustedRandIndex.of(y, model.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8842, r, 1E-4);
        assertEquals(0.2327, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.y));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.y));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.y));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.y));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.y));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.y));

        int[] p = new int[test.size()];
        for (int i = 0; i < test.size(); i++) {
            p[i] = model.predict(test.get(i).x());
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8782, r, 1E-4);
        assertEquals(0.2287, r2, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
