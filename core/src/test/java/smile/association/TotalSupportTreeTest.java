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

package smile.association;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.math.MathEx;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class TotalSupportTreeTest {

    int[][] itemsets = {
        {1, 3},
        {2},
        {4},
        {2, 3, 4},
        {2, 3},
        {2, 3},
        {1, 2, 3, 4},
        {1, 3},
        {1, 2, 3},
        {1, 2, 3}
    };
    
    public TotalSupportTreeTest() {
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
     * Test of getSupport method, of class TotalSupportTree.
     */
    @Test
    public void testGetSupport() {
        System.out.println("getSupport");
        FPGrowth fpgrowth = new FPGrowth(itemsets, 3);
        TotalSupportTree ttree = fpgrowth.buildTotalSupportTree();
        int[][] items = {
            {3, 2, 1},
            {3},
            {3, 1},
            {3, 2},
            {4},
            {2}
        };
        
        assertEquals(3, ttree.getSupport(items[0]));
        assertEquals(8, ttree.getSupport(items[1]));
        assertEquals(5, ttree.getSupport(items[2]));
        assertEquals(6, ttree.getSupport(items[3]));
        assertEquals(3, ttree.getSupport(items[4]));
        assertEquals(7, ttree.getSupport(items[5]));
    }

    /**
     * Test of getFrequentMaximalItemsets method, of class TotalSupportTree.
     */
    @Test
    public void testGetFrequentItemsets_0args() {
        System.out.println("getFrequentItemsets");
        FPGrowth fpgrowth = new FPGrowth(itemsets, 3);
        TotalSupportTree ttree = fpgrowth.buildTotalSupportTree();
        List<ItemSet> results = ttree.getFrequentItemsets();
        assertEquals(8, results.size());
        
        assertEquals(8, results.get(0).support);
        assertEquals(1, results.get(0).items.length);
        assertEquals(3, results.get(0).items[0]);
        
        assertEquals(7, results.get(1).support);
        assertEquals(1, results.get(1).items.length);
        assertEquals(2, results.get(1).items[0]);
        
        assertEquals(3, results.get(6).support);
        assertEquals(3, results.get(6).items.length);
        assertEquals(3, results.get(6).items[0]);
        assertEquals(2, results.get(6).items[1]);
        assertEquals(1, results.get(6).items[2]);
        
        assertEquals(3, results.get(7).support);
        assertEquals(1, results.get(7).items.length);
        assertEquals(4, results.get(7).items[0]);
    }

    /**
     * Test of getFrequentMaximalItemsets method, of class TotalSupportTree.
     */
    @Test
    public void testGetFrequentItemsets_PrintStream() {
        System.out.println("getFrequentItemsets");
        FPGrowth fpgrowth = new FPGrowth(itemsets, 3);
        TotalSupportTree ttree = fpgrowth.buildTotalSupportTree();
        long n = ttree.getFrequentItemsets(System.out);
        assertEquals(8, n);
    }
    
    /**
     * Test of getFrequentItemsets method, of class TotalSupportTree.
     */
    @Test
    public void testPima() {
        System.out.println("pima");

        List<int[]> dataList = new ArrayList<>(1000);

        try {
            BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("transaction/pima.D38.N768.C2");

            String line;
            for (int nrow = 0; (line = input.readLine()) != null; nrow++) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] s = line.split(" ");

                int[] point = new int[s.length];
                for (int i = 0; i < s.length; i++) {
                    point[i] = Integer.parseInt(s[i]);
                }

                dataList.add(point);
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

        int[][] data = dataList.toArray(new int[dataList.size()][]);

        int n = MathEx.max(data);
        System.out.format("%d transactions, %d items%n", data.length, n);
        
        long time = System.currentTimeMillis();
        FPGrowth fpgrowth = new FPGrowth(data, 20);
        System.out.format("Done building FP-tree: %.2f secs.%n", (System.currentTimeMillis() - time) / 1000.0);

        time = System.currentTimeMillis();
        TotalSupportTree ttree = fpgrowth.buildTotalSupportTree();
        System.out.format("Done building total support tree: %.2f secs.%n", (System.currentTimeMillis() - time) / 1000.0);
        
        time = System.currentTimeMillis();
        long numItemsets = ttree.getFrequentItemsets(System.out);
        System.out.format("%d frequent item sets discovered: %.2f secs.%n", numItemsets, (System.currentTimeMillis() - time) / 1000.0);
        
        assertEquals(1803, numItemsets);
        assertEquals(1803, ttree.getFrequentItemsets().size());
    }
    
    /**
     * Test of getFrequentItemsets method, of class TotalSupportTree.
     */
    @Test
    public void testKosarak() {
        System.out.println("kosarak");

        List<int[]> dataList = new ArrayList<>(1000);

        try {
            BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("transaction/kosarak.dat");

            String line;
            for (int nrow = 0; (line = input.readLine()) != null; nrow++) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] s = line.split(" ");

                Set<Integer> items = new HashSet<>();
                for (int i = 0; i < s.length; i++) {
                    items.add(Integer.parseInt(s[i]));
                }

                int j = 0;
                int[] point = new int[items.size()];
                for (int i : items) {
                    point[j++] = i;
                }

                dataList.add(point);
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

        int[][] data = dataList.toArray(new int[dataList.size()][]);

        int n = MathEx.max(data);
        System.out.format("%d transactions, %d items%n", data.length, n);
        
        long time = System.currentTimeMillis();
        FPGrowth fpgrowth = new FPGrowth(data, 1500);
        System.out.format("Done building FP-tree: %.2f secs.%n", (System.currentTimeMillis() - time) / 1000.0);

        time = System.currentTimeMillis();
        TotalSupportTree ttree = fpgrowth.buildTotalSupportTree();
        System.out.format("Done building total support tree: %.2f secs.%n", (System.currentTimeMillis() - time) / 1000.0);
        
        time = System.currentTimeMillis();
        //long numItemsets = ttree.getFrequentItemsets(System.out);
        //System.out.format("%d frequent item sets discovered: %.2f secs.%n", numItemsets, (System.currentTimeMillis() - time) / 1000.0);
        
        assertEquals(219725, ttree.getFrequentItemsets().size());
    }
}
