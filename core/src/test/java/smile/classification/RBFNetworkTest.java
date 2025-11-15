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
package smile.classification;

import smile.clustering.KMeans;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.math.rbf.GaussianRadialBasis;
import smile.model.rbf.RBF;
import smile.validation.*;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class RBFNetworkTest {

    public RBFNetworkTest() {
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

        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        ClassificationMetrics metrics = LOOCV.classification(iris.x(), iris.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 10)));

        System.out.println("RBF Network: " + metrics);
        assertEquals(0.9733, metrics.accuracy(), 1E-4);

        metrics = LOOCV.classification(iris.x(), iris.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 10), true));

        System.out.println("Normalized RBF Network: " + metrics);
        assertEquals(0.9867, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var result = CrossValidation.classification(10, pen.x(), pen.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 50)));

        System.out.println("RBF Network: " + result);
        assertEquals(0.9, result.avg().accuracy(), 1E-4);

        result = CrossValidation.classification(10, pen.x(), pen.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 50), true));

        System.out.println("Normalized RBF Network: " + result);
        assertEquals(0.9172, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var result = CrossValidation.classification(10, cancer.x(), cancer.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 30)));

        System.out.println("RBF Network: " + result);
        assertEquals(0.9477, result.avg().accuracy(), 1E-4);

        result = CrossValidation.classification(10, cancer.x(), cancer.y(),
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 30), true));

        System.out.println("Normalized RBF Network: " + result);
        assertEquals(0.9421, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        double[][] x = segment.x();
        double[][] testx = segment.testx();
        int[] y = segment.y();
        int[] testy = segment.testy();
        MathEx.standardize(x);
        MathEx.standardize(testx);

        RBFNetwork<double[]> model = RBFNetwork.fit(x, y, RBF.fit(x, 30));
        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.println("RBF Network Error = " + error);
        assertEquals(127, error);

        model = RBFNetwork.fit(x, y, RBF.fit(x, 30), true);
        prediction = model.predict(testx);
        error = Error.of(testy, prediction);
        System.out.println("Normalized RBF Network Error = " + error);
        assertEquals(116, error);
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
        var kmeans = KMeans.fit(x, 200, 10);
        RBF<double[]>[] neurons = RBF.of(kmeans.centers(), new GaussianRadialBasis(8.0), MathEx::distance);

        RBFNetwork<double[]> model = RBFNetwork.fit(x, y, neurons);
        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.println("RBF Network Error = " + error);
        assertEquals(135, error);

        model = RBFNetwork.fit(x, y, neurons, true);
        prediction = model.predict(testx);
        error = Error.of(testy, prediction);
        System.out.println("Normalized RBF Network Error = " + error);
        assertEquals(137, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
