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
package smile.model;

import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.data.formula.*;
import smile.datasets.ImageSegmentation;
import smile.feature.transform.Standardizer;
import smile.feature.transform.WinsorScaler;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Karl Li
 */
public class ClassificationModelTest {

    public ClassificationModelTest() {
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
    public void testRandomForest() throws Exception {
        System.out.println("Random Forest");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        var params = new Properties();
        params.setProperty("smile.random_forest.trees", "100");
        params.setProperty("smile.random_forest.max_nodes", "100");
        var model = Model.classification("random-forest", segment.formula(), segment.train(), segment.test(), params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(34, model.test().error(), 3);
    }

    @Test
    public void testSVM() throws Exception {
        System.out.println("SVM");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        var scaler = Standardizer.fit(segment.train());
        var train = scaler.apply(segment.train());
        var test = scaler.apply(segment.test());
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.svm.kernel", "Gaussian(6.4)");
        params.setProperty("smile.svm.C", "100");
        params.setProperty("smile.svm.type", "ovo");
        var model = Model.classification("svm", segment.formula(), train, test, params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(33, model.test().error(), 3);
    }

    @Test
    public void testEnsemble() throws Exception {
        System.out.println("SVM Ensemble");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        var scaler = Standardizer.fit(segment.train());
        var train = scaler.apply(segment.train());
        var test = scaler.apply(segment.test());
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.svm.kernel", "Gaussian(6.4)");
        params.setProperty("smile.svm.C", "100");
        params.setProperty("smile.svm.type", "ovo");
        var model = Model.classification("svm", segment.formula(), train, test, params, 5, 3, true);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(30, model.test().error(), 3);
    }

    @Test
    public void testMLP() throws Exception {
        System.out.println("MLP");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        var scaler = WinsorScaler.fit(segment.train(), 0.01, 0.99);
        var train = scaler.apply(segment.train());
        var test = scaler.apply(segment.test());
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.mlp.epochs", "13");
        params.setProperty("smile.mlp.mini_batch", "20");
        params.setProperty("smile.mlp.layers", "Sigmoid(50)");
        params.setProperty("smile.mlp.learning_rate", "linear(0.1, 10000, 0.01)");
        params.setProperty("smile.mlp.RMSProp.rho", "0.9");
        var model = Model.classification("mlp", segment.formula(), train, test, params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(32, model.test().error());
    }
}
