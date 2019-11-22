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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
@SuppressWarnings("unused")
public class FPGrowthTest {
    
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

    int[][] itemsets2 = {
            {1, 2, 3, 4}
    };
    
    public FPGrowthTest() {
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

    @Test
    public void test() {
        System.out.println("FP-Growth");
        FPTree tree = FPTree.of(3, itemsets);
        List<ItemSet> results = FPGrowth.apply(tree).collect(Collectors.toList());
        assertEquals(8, results.size());

        assertEquals(3, results.get(0).support);
        assertEquals(1, results.get(0).items.length);
        assertEquals(4, results.get(0).items[0]);
        
        assertEquals(5, results.get(1).support);
        assertEquals(1, results.get(1).items.length);
        assertEquals(1, results.get(1).items[0]);
        
        assertEquals(6, results.get(6).support);
        assertEquals(2, results.get(6).items.length);
        assertEquals(3, results.get(6).items[0]);
        assertEquals(2, results.get(6).items[1]);
        
        assertEquals(8, results.get(7).support);
        assertEquals(1, results.get(7).items.length);
        assertEquals(3, results.get(7).items[0]);
    }

    @Test
    public void testSinglePath() {
        System.out.println("single path");

        FPTree tree = FPTree.of(1, itemsets2);
        assertEquals(15, FPGrowth.apply(tree).count());
    }

    @Test(expected = Test.None.class)
    public void testPima() throws IOException {
        System.out.println("pima");

        FPTree tree = FPTree.of(20, () -> ItemSetTestData.read("transaction/pima.D38.N768.C2"));
        assertEquals(1803, FPGrowth.apply(tree).count());
    }
    
    @Test(expected = Test.None.class)
    public void testKosarak() throws IOException {
        System.out.println("kosarak");

        FPTree tree = FPTree.of(1500, () -> ItemSetTestData.read("transaction/kosarak.dat"));
        assertEquals(219725, FPGrowth.apply(tree).count());
    }
}
