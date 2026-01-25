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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import smile.data.SparseDataset;
import smile.io.Read;
import smile.io.Write;
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.util.SparseIntArray;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SpectralClusteringTest {
    double[][] x;
    int[] y;
    public SpectralClusteringTest() throws Exception {
        var usps = new USPS();
        x = usps.x();
        y = usps.y();
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

        var model = SpectralClustering.fit(x, new SpectralClustering.Options(10, 8.0, 100));
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9129, r, 1E-4);
        assertEquals(0.5382, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));
    }

    @Test
    public void testUSPSNystrom() throws Exception {
        System.out.println("USPS Nystrom approximation");
        MathEx.setSeed(19650218); // to get repeatable results.

        var model = SpectralClustering.fit(x, new SpectralClustering.Options(10, 100, 8.0, 100));
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8994, r, 1E-4);
        assertEquals(0.4752, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testNews() throws Exception {
        System.out.println("News");
        MathEx.setSeed(19650218); // to get repeatable results.

        SparseDataset<Integer> news = Read.libsvm(smile.io.Paths.getTestData("libsvm/news20.dat"));
        int n = 2000; // partial data to reduce test time
        int p = news.ncol();

        int[] y = new int[n];
        SparseIntArray[] data = new SparseIntArray[n];
        for (int i = 0; i < n; i++) {
            var instance = news.get(i);
            var row = instance.x();
            y[i] = instance.y();
            var count = new SparseIntArray(row.size());
            row.forEach((j, value) -> count.set(j, (int) value));
            data[i] = count;
        }

        var model = SpectralClustering.fit(data, p, new Clustering.Options(20, 100));
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8952, r, 3E-2);
        assertEquals(0.0380, r2, 3E-2);
    }
}
