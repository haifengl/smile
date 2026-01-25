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
package smile.classification;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.Hyphen;
import smile.datasets.Protein;
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
        var protein = new Protein();
        Maxent model = Maxent.fit(protein.train().p(), protein.train().x(), protein.train().y());

        int[] prediction = model.predict(protein.test().x());
        int error = Error.of(prediction, protein.test().y());

        System.out.format("The error is %d of %d%n", error, protein.test().x().length);
        assertEquals(1339, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testHyphen() throws Exception {
        System.out.println("hyphen");
        var hyphen = new Hyphen();
        Maxent model = Maxent.fit(hyphen.train().p(), hyphen.train().x(), hyphen.train().y());

        int[] prediction = model.predict(hyphen.test().x());
        int error = Error.of(prediction, hyphen.test().y());

        System.out.format("The error is %d of %d%n", error, hyphen.test().x().length);
        assertEquals(762, error);
    }
}
