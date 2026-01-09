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
package smile.model;

import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.data.formula.*;
import smile.datasets.ProstateCancer;
import smile.feature.transform.WinsorScaler;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Karl Li
 */
public class RegressionModelTest {

    public RegressionModelTest() {
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
        var prostate = new ProstateCancer();
        var params = new Properties();
        params.setProperty("smile.random_forest.trees", "100");
        params.setProperty("smile.random_forest.max.nodes", "100");
        var model = Model.regression("random-forest", prostate.formula(), prostate.train(), prostate.test(), params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(0.484, model.test().r2(), 0.03);
    }

    @Test
    public void testSVM() throws Exception {
        System.out.println("SVM");
        MathEx.setSeed(19650218); // to get repeatable results.
        var prostate = new ProstateCancer();
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.svm.kernel", "Gaussian(6.0)");
        params.setProperty("smile.svm.C", "5");
        params.setProperty("smile.svm.epsilon", "0.5");
        var model = Model.regression("svm", prostate.formula(), prostate.train(), prostate.test(), params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(0.209, model.test().r2(), 0.01);
    }

    @Test
    public void testEnsemble() throws Exception {
        System.out.println("SVM Ensemble");
        MathEx.setSeed(19650218); // to get repeatable results.
        var prostate = new ProstateCancer();
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.svm.kernel", "Gaussian(6.0)");
        params.setProperty("smile.svm.C", "5");
        params.setProperty("smile.svm.epsilon", "0.5");
        var model = Model.regression("svm", prostate.formula(), prostate.train(), prostate.test(), params, 5, 3, true);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(0.218, model.test().r2(), 0.01);
    }

    @Test
    public void testMLP() throws Exception {
        System.out.println("MLP");
        MathEx.setSeed(19650218); // to get repeatable results.
        var prostate = new ProstateCancer();
        var scaler = WinsorScaler.fit(prostate.train(), 0.01, 0.99);
        var train = scaler.apply(prostate.train());
        var test = scaler.apply(prostate.test());
        var params = new Properties();
        // This property is not supported now.
        // params.setProperty("smile.feature.transform", "Standardizer")
        params.setProperty("smile.mlp.epochs", "30");
        params.setProperty("smile.mlp.activation", "ReLU(50)|Sigmoid(30)");
        params.setProperty("smile.mlp.learning_rate", "0.2");
        var model = Model.regression("mlp", prostate.formula(), train, test, params);
        System.out.println("Training metrics: " + model.train());
        System.out.println("Validation metrics: " + model.validation());
        System.out.println("Test metrics: " + model.test());
        assertEquals(0.398, model.test().r2(), 0.01);
    }
}
