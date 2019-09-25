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

package smile.neighbor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.distance.EditDistance;

/**
 *
 * @author Haifeng Li
 */
public class CoverTreeStringSpeedTest {

    List<String> words = new ArrayList<>();
    CoverTree<String> cover;

    public CoverTreeStringSpeedTest() {
        long start = System.currentTimeMillis();
        try {
            InputStreamReader reader = new FileReader(smile.util.Paths.getTestData("neighbor/index.noun").toFile());
            BufferedReader input = new BufferedReader(reader);
            String line = input.readLine();
            while (line != null) {
                if (!line.startsWith(" ")) {
                    String[] w = line.split("\\s");
                    words.add(w[0].replace('_', ' '));
                }
                line = input.readLine();
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Loading data: %.2fs%n", time);

        String[] data = words.toArray(new String[words.size()]);

        start = System.currentTimeMillis();
        cover = new CoverTree<>(data, new EditDistance(50, true));
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs%n", time);
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

    /**
     * Test of range method, of class Naive.
     */
    @Test
    public void testNaiveSpeed() {
        System.out.println("cover tree");
        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            cover.range(words.get(i), 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Cover tree string search: %.2fs%n", time);
    }
}