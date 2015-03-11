/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.neighbor;

import java.io.BufferedReader;
import java.io.InputStream;
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

    List<String> words = new ArrayList<String>();
    CoverTree<String> cover;

    public CoverTreeStringSpeedTest() {
        long start = System.currentTimeMillis();
        try {
            InputStream stream = this.getClass().getResourceAsStream("/smile/neighbor/index.noun");
            BufferedReader input = new BufferedReader(new InputStreamReader(stream));
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
        System.out.format("Loading data: %.2fs\n", time);

        String[] data = words.toArray(new String[words.size()]);

        start = System.currentTimeMillis();
        cover = new CoverTree<String>(data, new EditDistance(50, true));
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs\n", time);
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
        List<Neighbor<String, String>> neighbors = new ArrayList<Neighbor<String, String>>();
        for (int i = 1000; i < 1100; i++) {
            cover.range(words.get(i), 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Cover tree string search: %.2fs\n", time);
    }
}