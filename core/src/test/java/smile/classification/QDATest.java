/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.classification;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.BreastCancer;
import smile.data.Iris;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.Error;
import smile.validation.LOOCV;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class QDATest {

    public QDATest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test(expected = Test.None.class)
    public void testIris() throws Exception {
        System.out.println("Iris");

        int[] prediction = LOOCV.classification(Iris.x, Iris.y, (x, y) -> QDA.fit(x, y));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(4, error);

        QDA model = QDA.fit(Iris.x, Iris.y);
        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, (x, y) -> QDA.fit(x, y));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(24, error);
    }
}