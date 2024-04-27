/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.test.data.BreastCancer;
import smile.test.data.Iris;
import smile.validation.ClassificationValidations;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.ClassificationMetrics;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class QDATest {

    public QDATest() {
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

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, QDA::fit);

        System.out.println(metrics);
        assertEquals(0.9733, metrics.accuracy, 1E-4);

        QDA model = QDA.fit(Iris.x, Iris.y);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<QDA> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, QDA::fit);

        System.out.println(result);
        assertEquals(0.9589, result.avg.accuracy, 1E-4);
    }
}