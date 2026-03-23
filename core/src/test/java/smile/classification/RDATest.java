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
package smile.classification;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.BreastCancer;
import smile.datasets.Iris;
import smile.datasets.PenDigits;
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RDATest {

    public RDATest() {
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
    public void testIris() throws Exception {
        System.out.println("Iris");

        var iris = new Iris();
        int[] expected = {22, 24, 20, 19, 16, 12, 11, 9, 6, 3, 4};
        for (int i = 0; i <= 10; i++) {
            double alpha = i * 0.1;
            ClassificationMetrics metrics = LOOCV.classification(iris.x(), iris.y(), (x, y) -> RDA.fit(x, y, alpha));

            System.out.format("alpha = %.1f, metrics = %s%n", alpha, metrics);
            assertEquals(1.0 - expected[i]/150.0, metrics.accuracy(), 1E-4);
        }
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var result = CrossValidation.classification(10, pen.x(), pen.y(),
                (x, y) -> RDA.fit(x, y, 0.9));

        System.out.println(result);
        assertEquals(0.9863, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var result = CrossValidation.classification(10, cancer.x(), cancer.y(),
                (x, y) -> RDA.fit(x, y, 0.9));

        System.out.println(result);
        assertEquals(0.9461, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        RDA model = RDA.fit(usps.x(), usps.y(), 0.7);

        int[] prediction = model.predict(usps.testx());
        int error = Error.of(usps.testy(), prediction);

        System.out.println("Error = " + error);
        assertEquals(235, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
