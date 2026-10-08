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
package smile.spark;

import java.util.List;
import java.util.Properties;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.hpo.Hyperparameters;
import smile.io.Paths;
import smile.io.Read;
import smile.validation.ClassificationValidations;

import static org.junit.jupiter.api.Assertions.*;

class SparkHPOTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("SparkHPOTest")
                .getOrCreate();
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void testClassificationCrossValidation() throws Exception {
        DataFrame mushrooms = Read.arff(Paths.getTestData("weka/mushrooms.arff")).dropna();
        Formula formula = Formula.lhs("class");

        Hyperparameters hp = new Hyperparameters()
                .add("smile.random.forest.trees", 50)
                .add("smile.random.forest.mtry", new int[]{2, 3})
                .add("smile.random.forest.max.nodes", 50, 150, 50);

        List<Properties> configurations = hp.random().limit(3).toList();

        List<ClassificationValidations<RandomForest>> scores = SparkHPO.classification(
                spark, 3, formula, mushrooms, configurations,
                (f, data, props) -> RandomForest.fit(f, data, RandomForest.Options.of(props))
        );

        assertEquals(configurations.size(), scores.size());
        for (var validation : scores) {
            assertNotNull(validation);
            assertTrue(validation.avg().accuracy() > 0.9);
        }
    }
}
