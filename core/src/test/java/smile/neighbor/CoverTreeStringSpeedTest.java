/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.neighbor;

import java.io.BufferedReader;
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
            BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("neighbor/index.noun");
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