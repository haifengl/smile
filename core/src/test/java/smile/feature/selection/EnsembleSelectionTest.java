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
package smile.feature.selection;

import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.classification.AdaBoost;
import smile.classification.DecisionTree;
import smile.classification.GradientTreeBoost;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.Abalone;
import smile.datasets.Iris;
import smile.math.MathEx;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EnsembleSelection.
 *
 * @author Haifeng Li
 */
public class EnsembleSelectionTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testClassificationIris() throws Exception {
        var iris = new Iris();
        DataFrame data = iris.data();
        EnsembleSelection[] scores = EnsembleSelection.fit(data, "class");

        assertEquals(4, scores.length);
        for (var s : scores) {
            assertTrue(s.importance() >= 0.0, "Importance should be non-negative: " + s);
            assertNotNull(s.feature());
        }

        // Top-2 features
        String[] top2 = EnsembleSelection.top(scores, 2);
        assertEquals(2, top2.length);
        // On Iris, petal length / width are well-known to have highest importance
        assertTrue(Arrays.asList(top2).contains("petallength") || Arrays.asList(top2).contains("petalwidth"));

        // Threshold selection
        String[] selected = EnsembleSelection.threshold(scores, 0.0);
        assertEquals(4, selected.length);
    }

    @Test
    public void testRegressionAbalone() throws Exception {
        var abalone = new Abalone();
        DataFrame data = abalone.train();
        EnsembleSelection[] scores = EnsembleSelection.fit(Formula.lhs("rings"), data);

        assertEquals(8, scores.length);
        for (var s : scores) {
            assertTrue(s.importance() >= 0.0, "Importance should be non-negative: " + s);
        }

        String[] top3 = EnsembleSelection.top(scores, 3);
        assertEquals(3, top3.length);
    }

    @Test
    public void testFromClassificationModels() throws Exception {
        var iris = new Iris();
        DataFrame data = iris.data();
        Formula formula = Formula.lhs("class");

        // Random Forest
        var rf = RandomForest.fit(formula, data);
        EnsembleSelection[] rfScores = EnsembleSelection.of(rf);
        assertEquals(4, rfScores.length);

        // Gradient Tree Boost
        var gbt = GradientTreeBoost.fit(formula, data);
        EnsembleSelection[] gbtScores = EnsembleSelection.of(gbt);
        assertEquals(4, gbtScores.length);

        // AdaBoost
        var ada = AdaBoost.fit(formula, data);
        EnsembleSelection[] adaScores = EnsembleSelection.of(ada);
        assertEquals(4, adaScores.length);

        // Decision Tree
        var dt = DecisionTree.fit(formula, data);
        EnsembleSelection[] dtScores = EnsembleSelection.of(dt);
        assertEquals(4, dtScores.length);
    }

    @Test
    public void testFromRegressionModels() throws Exception {
        var abalone = new Abalone();
        DataFrame data = abalone.train();
        Formula formula = Formula.lhs("rings");

        // Random Forest Regression
        var rf = smile.regression.RandomForest.fit(formula, data);
        EnsembleSelection[] rfScores = EnsembleSelection.of(rf);
        assertEquals(8, rfScores.length);

        // Gradient Tree Boost Regression
        var gbt = smile.regression.GradientTreeBoost.fit(formula, data);
        EnsembleSelection[] gbtScores = EnsembleSelection.of(gbt);
        assertEquals(8, gbtScores.length);

        // Regression Tree
        var rt = smile.regression.RegressionTree.fit(formula, data);
        EnsembleSelection[] rtScores = EnsembleSelection.of(rt);
        assertEquals(8, rtScores.length);
    }

    @Test
    public void testFitWithOptions() throws Exception {
        var iris = new Iris();
        DataFrame data = iris.data();
        Properties props = new Properties();
        props.setProperty("smile.random_forest.trees", "50");

        EnsembleSelection[] scores = EnsembleSelection.fit(data, "class", props);
        assertEquals(4, scores.length);
    }

    @Test
    public void testOfSchemaAndImportance() {
        StructType schema = new StructType(
                new StructField("f1", DataTypes.DoubleType),
                new StructField("f2", DataTypes.DoubleType),
                new StructField("f3", DataTypes.DoubleType)
        );
        double[] importance = {0.1, 0.5, 0.4};

        EnsembleSelection[] scores = EnsembleSelection.of(schema, importance);
        assertEquals(3, scores.length);
        assertEquals("f1", scores[0].feature());
        assertEquals(0.1, scores[0].importance(), 1E-6);

        // Sorting
        Arrays.sort(scores);
        assertEquals("f1", scores[0].feature());
        assertEquals("f3", scores[1].feature());
        assertEquals("f2", scores[2].feature());

        // Top 2
        String[] top2 = EnsembleSelection.top(scores, 2);
        assertArrayEquals(new String[]{"f2", "f3"}, top2);

        // Threshold >= 0.35
        String[] above = EnsembleSelection.threshold(scores, 0.35);
        assertArrayEquals(new String[]{"f2", "f3"}, above);

        // Mismatched length throws
        assertThrows(IllegalArgumentException.class, () ->
                EnsembleSelection.of(schema, new double[]{0.1, 0.2})
        );
    }

    @Test
    public void testTopInvalidK() {
        EnsembleSelection[] scores = {
                new EnsembleSelection("a", 1.0)
        };
        assertThrows(IllegalArgumentException.class, () -> EnsembleSelection.top(scores, 0));
    }
}
