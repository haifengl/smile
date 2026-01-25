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
package smile.association;

import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

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
    public void test() {
        System.out.println("T-Tree");

        FPTree tree = FPTree.of(3, itemsets);
        TotalSupportTree ttree = new TotalSupportTree(tree);

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

        List<ItemSet> results = ttree.stream().toList();
        for (var itemSet : results) {
            System.out.println(itemSet);
        }

        assertEquals(8, results.size());
        
        assertEquals(8, results.get(0).support());
        assertEquals(1, results.get(0).items().length);
        assertEquals(3, results.get(0).items()[0]);
        
        assertEquals(7, results.get(1).support());
        assertEquals(1, results.get(1).items().length);
        assertEquals(2, results.get(1).items()[0]);
        
        assertEquals(3, results.get(6).support());
        assertEquals(3, results.get(6).items().length);
        assertEquals(3, results.get(6).items()[0]);
        assertEquals(2, results.get(6).items()[1]);
        assertEquals(1, results.get(6).items()[2]);
        
        assertEquals(3, results.get(7).support());
        assertEquals(1, results.get(7).items().length);
        assertEquals(4, results.get(7).items()[0]);
    }
    
    @Test
    public void testPima() {
        System.out.println("pima");

        FPTree tree = FPTree.of(20, () -> ItemSetTestData.read("transaction/pima.D38.N768.C2"));
        TotalSupportTree ttree = new TotalSupportTree(tree);
        assertEquals(1803, ttree.stream().count());
    }
    
    @Test
    public void testKosarak() {
        System.out.println("kosarak");

        FPTree tree = FPTree.of(1500, () -> ItemSetTestData.read("transaction/kosarak.dat"));
        TotalSupportTree ttree = new TotalSupportTree(tree);
        assertEquals(219725, ttree.stream().count());
    }
}
