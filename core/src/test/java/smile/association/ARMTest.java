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
import java.util.stream.Stream;
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
@SuppressWarnings("unused")
public class ARMTest {

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

    public ARMTest() {
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
        System.out.println("ARM");
        FPTree tree = FPTree.of(3, itemsets);
        List<AssociationRule> rules = ARM.apply(0.5, tree).collect(Collectors.toList());
        assertEquals(9, rules.size());
        
        assertEquals(0.6, rules.get(0).support, 1E-2);
        assertEquals(0.75, rules.get(0).confidence, 1E-2);
        assertEquals(1, rules.get(0).antecedent.length);
        assertEquals(3, rules.get(0).antecedent[0]);
        assertEquals(1, rules.get(0).consequent.length);
        assertEquals(2, rules.get(0).consequent[0]);

        
        assertEquals(0.3, rules.get(4).support, 1E-2);
        assertEquals(0.6, rules.get(4).confidence, 1E-2);
        assertEquals(1, rules.get(4).antecedent.length);
        assertEquals(1, rules.get(4).antecedent[0]);
        assertEquals(1, rules.get(4).consequent.length);
        assertEquals(2, rules.get(4).consequent[0]);
        
        assertEquals(0.3, rules.get(8).support, 1E-2);
        assertEquals(0.6, rules.get(8).confidence, 1E-2);
        assertEquals(1, rules.get(8).antecedent.length);
        assertEquals(1, rules.get(8).antecedent[0]);
        assertEquals(2, rules.get(8).consequent.length);
        assertEquals(3, rules.get(8).consequent[0]);
        assertEquals(2, rules.get(8).consequent[1]);
    }

    @Test(expected = Test.None.class)
    public void testPima() throws IOException {
        System.out.println("pima");

        FPTree tree = FPTree.of(20, () -> ItemSetTestData.read("transaction/pima.D38.N768.C2"));
        Stream<AssociationRule> rules = ARM.apply(0.9, tree);
        assertEquals(6803, rules.count());
    }

    @Test(expected = Test.None.class)
    public void testKosarak() throws IOException {
        System.out.println("kosarak");

        FPTree tree = FPTree.of(0.003, ()-> ItemSetTestData.read("transaction/kosarak.dat"));
        Stream<AssociationRule> rules = ARM.apply(0.5, tree);
        assertEquals(17954, rules.count());
    }
}
