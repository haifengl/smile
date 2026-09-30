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
package smile.hpo;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.datasets.Iris;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.metric.Accuracy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for {@link BayesianOptimization}.
 *
 * @author Haifeng Li
 */
public class BayesianOptimizationTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(20260930);
    }

    @Test
    public void test2DParaboloidMaximization() {
        // True optimum at x = 2.5, y = 3.5 with f(x, y) = 10.0
        var hp = new Hyperparameters()
                .add("x", 0.0, 5.0)
                .add("y", 0.0, 5.0);

        var result = hp.bayes(props -> {
            double x = Double.parseDouble(props.getProperty("x"));
            double y = Double.parseDouble(props.getProperty("y"));
            return 10.0 - (x - 2.5) * (x - 2.5) - (y - 3.5) * (y - 3.5);
        }, 25);

        assertNotNull(result);
        assertEquals(25, result.trials().size());
        assertTrue(result.value() >= 9.5, "Expected near-optimal value >= 9.5, got: " + result.value());

        double bestX = Double.parseDouble(result.best().getProperty("x"));
        double bestY = Double.parseDouble(result.best().getProperty("y"));
        assertEquals(2.5, bestX, 0.6);
        assertEquals(3.5, bestY, 0.6);
    }

    @Test
    public void test2DSphereMinimization() {
        // True optimum at x = 1.0, y = 2.0 with f(x, y) = 0.0
        var hp = new Hyperparameters()
                .add("x", -2.0, 4.0)
                .add("y", -2.0, 4.0);

        var result = hp.bayes(props -> {
            double x = Double.parseDouble(props.getProperty("x"));
            double y = Double.parseDouble(props.getProperty("y"));
            return (x - 1.0) * (x - 1.0) + (y - 2.0) * (y - 2.0);
        }, 25, false);

        assertNotNull(result);
        assertEquals(25, result.trials().size());
        assertTrue(result.value() <= 0.35, "Expected minimized value <= 0.35, got: " + result.value());

        double bestX = Double.parseDouble(result.best().getProperty("x"));
        double bestY = Double.parseDouble(result.best().getProperty("y"));
        assertEquals(1.0, bestX, 0.6);
        assertEquals(2.0, bestY, 0.6);
    }

    @Test
    public void testMixedParameterTypes() {
        var hp = new Hyperparameters()
                .add("fixed_str", "smile")
                .add("fixed_int", 42)
                .add("category", new String[]{"linear", "poly", "rbf"})
                .add("int_choice", new int[]{10, 20, 50, 100})
                .add("double_choice", new double[]{0.01, 0.1, 1.0})
                .add("int_range", 1, 10, 1)
                .add("double_range", 0.0, 1.0);

        var result = hp.bayes(props -> {
            assertEquals("smile", props.getProperty("fixed_str"));
            assertEquals("42", props.getProperty("fixed_int"));
            String cat = props.getProperty("category");
            int intChoice = Integer.parseInt(props.getProperty("int_choice"));
            double dChoice = Double.parseDouble(props.getProperty("double_choice"));
            int iRange = Integer.parseInt(props.getProperty("int_range"));
            double dRange = Double.parseDouble(props.getProperty("double_range"));

            double score = (cat.equals("rbf") ? 2.0 : 0.0)
                    + (intChoice == 50 ? 1.5 : 0.0)
                    + (dChoice == 0.1 ? 1.0 : 0.0)
                    + (iRange == 7 ? 1.0 : 0.0)
                    - Math.abs(dRange - 0.75);
            return score;
        }, 20);

        assertNotNull(result);
        assertEquals(20, result.trials().size());
        assertTrue(result.value() > 0.0);
    }

    @Test
    public void testAcquisitionStrategies() {
        var hp = new Hyperparameters().add("x", -5.0, 5.0);

        for (var acq : BayesianOptimization.Acquisition.values()) {
            var options = new BayesianOptimization.Options(15)
                    .withAcquisition(acq, 0.05);

            var result = hp.bayes(props -> {
                double x = Double.parseDouble(props.getProperty("x"));
                return -(x * x); // max at x = 0
            }, options);

            assertNotNull(result);
            assertEquals(15, result.trials().size());
            assertTrue(result.value() >= -2.0, "Acquisition " + acq + " should find near 0, got: " + result.value());
        }
    }

    @Test
    public void testAllParametersFixed() {
        var hp = new Hyperparameters()
                .add("fixed_a", 10)
                .add("fixed_b", "const");

        var result = hp.bayes(props -> {
            assertEquals("10", props.getProperty("fixed_a"));
            assertEquals("const", props.getProperty("fixed_b"));
            return 42.0;
        }, 10);

        assertNotNull(result);
        assertEquals(1, result.trials().size());
        assertEquals(42.0, result.value());
    }

    @Test
    public void testRealModelTuningOnIris() throws Exception {
        var iris = new Iris();
        DataFrame data = iris.data();
        Formula formula = Formula.lhs("class");

        var hp = new Hyperparameters()
                .add("smile.random_forest.trees", new int[]{50, 100})
                .add("smile.random_forest.max_depth", 3, 10, 1)
                .add("smile.random_forest.node_size", new int[]{1, 3, 5});

        var result = hp.bayes(props -> {
            RandomForest model = RandomForest.fit(formula, data, RandomForest.Options.of(props));
            int[] pred = model.predict(data);
            int[] truth = formula.y(data).toIntArray();
            return Accuracy.of(truth, pred);
        }, 12);

        assertNotNull(result);
        assertEquals(12, result.trials().size());
        assertTrue(result.value() >= 0.95, "Expected high accuracy on Iris training set, got: " + result.value());
        assertNotNull(result.best().getProperty("smile.random_forest.trees"));
    }

    @Test
    public void testSerialization() throws Exception {
        var hp = new Hyperparameters().add("x", 0.0, 1.0);
        var result = hp.bayes(props -> Double.parseDouble(props.getProperty("x")), 5);

        Path temp = Write.object(result);
        Object restored = Read.object(temp);

        assertNotNull(restored);
        assertInstanceOf(BayesianOptimization.Result.class, restored);
        var r = (BayesianOptimization.Result) restored;
        assertEquals(result.value(), r.value(), 1E-10);
        assertEquals(result.trials().size(), r.trials().size());
    }

    @Test
    public void testValidationAndExceptions() {
        assertThrows(NullPointerException.class, () -> BayesianOptimization.fit(null, p -> 1.0, 10));
        assertThrows(NullPointerException.class, () -> BayesianOptimization.fit(new Hyperparameters(), null, 10));

        var emptyHp = new Hyperparameters();
        assertThrows(IllegalStateException.class, () -> emptyHp.bayes(p -> 1.0, 10));

        var hp = new Hyperparameters().add("x", 1.0, 5.0);
        assertThrows(IllegalArgumentException.class, () -> hp.bayes(p -> 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> hp.bayes(p -> 1.0, -5));
    }
}
