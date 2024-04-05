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
import smile.test.data.Hyphen;
import smile.test.data.Protein;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class MaxentTest {

    public MaxentTest() {
        
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
    public void testProtein() throws Exception {
        System.out.println("protein");

        Maxent model = Maxent.fit(Protein.p, Protein.x, Protein.y);

        int[] prediction = model.predict(Protein.testx);
        int error = Error.of(prediction, Protein.testy);

        System.out.format("The error is %d of %d%n", error, Protein.testx.length);
        assertEquals(1339, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testHyphen() {
        System.out.println("hyphen");

        Maxent model = Maxent.fit(Hyphen.p, Hyphen.x, Hyphen.y);

        int[] prediction = model.predict(Hyphen.testx);
        int error = Error.of(prediction, Hyphen.testy);

        System.out.format("The error is %d of %d%n", error, Hyphen.testx.length);
        assertEquals(762, error);
    }
}
