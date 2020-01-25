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

import java.io.IOException;
import smile.data.Hyphen;
import smile.data.Protein;
import smile.validation.Error;
import smile.validation.Validation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MaxentTest {

    public MaxentTest() {
        
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
    public void testProtein() throws Exception {
        System.out.println("protein");

        Maxent model = Maxent.fit(Protein.p, Protein.x, Protein.y);

        int[] prediction = Validation.test(model, Protein.testx);
        int error = Error.of(prediction, Protein.testy);

        System.out.format("The error is %d of %d%n", error, Protein.testx.length);
        assertEquals(1339, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test(expected = Test.None.class)
    public void testHyphen() throws IOException {
        System.out.println("hyphen");

        Maxent model = Maxent.fit(Hyphen.p, Hyphen.x, Hyphen.y);

        int[] prediction = Validation.test(model, Hyphen.testx);
        int error = Error.of(prediction, Hyphen.testy);

        System.out.format("The error is %d of %d%n", error, Hyphen.testx.length);
        assertEquals(762, error);
    }
}
