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

package smile.taxonomy;

import java.util.LinkedList;
import java.util.List;
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
public class TaxonomyTest {

    Taxonomy instance = null;
    Concept a, b, c, d, e, f, ad;

    public TaxonomyTest() {
        instance = new Taxonomy("MyTaxo");
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

  /**
   * Create the following taxonomy:
   *
   *       --|---
   *       |    |
   *    ---|--  |
   *    |    |  |
   *  --A--  |  |
   *  |   |  |  |
   *  B   C  D  E
   */
    @Before
    public void setUp() {
        // Creating Taxonomy
        String[] concepts = {"A", "B", "C", "D", "E", "F"};

        Concept root = instance.getRoot();
        ad = root.addChild("");
        e = root.addChild("E");
        d = ad.addChild("D");
        a = ad.addChild("A");
        b = a.addChild("B");
        c = a.addChild("C");
        f = c.addChild("F");

        System.out.println("Taxonomy created:\n");
        System.out.println("       --|---");
        System.out.println("       |    |");
        System.out.println("    ---|--  |");
        System.out.println("    |    |  |");
        System.out.println("  --A--  |  |");
        System.out.println("  |   |  |  |");
        System.out.println("  B   C  D  E");
        System.out.println("      |      ");
        System.out.println("      F      ");
        System.out.println();

        System.out.println();
        for (int i = 0; i < concepts.length; i++) {
            System.out.println(instance.getConcept(concepts[i]));
        }
        System.out.println();
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of lowestCommonAncestor method, of class Taxonomy.
     */
    @Test
    public void testLowestCommonAncestor() {
        System.out.println("lowestCommonAncestor");
        Concept result = instance.lowestCommonAncestor("A", "B");
        assertEquals(a, result);

        result = instance.lowestCommonAncestor("E", "B");
        assertEquals(instance.getRoot(), result);
    }

    /**
     * Test of getPathToRoot method, of class Taxonomy.
     */
    @Test
    public void testGetPathToRoot() {
        System.out.println("getPathToRoot");
        LinkedList<Concept> expResult = new LinkedList<>();
        expResult.addFirst(instance.getRoot());
        expResult.addFirst(ad);
        expResult.addFirst(a);
        expResult.addFirst(c);
        expResult.addFirst(f);
        List<Concept> result = f.getPathToRoot();
        assertEquals(expResult, result);
    }

    /**
     * Test of getPathFromRoot method, of class Taxonomy.
     */
    @Test
    public void testGetPathFromRoot() {
        System.out.println("getPathToRoot");
        LinkedList<Concept> expResult = new LinkedList<>();
        expResult.add(instance.getRoot());
        expResult.add(ad);
        expResult.add(a);
        expResult.add(c);
        expResult.add(f);
        List<Concept> result = f.getPathFromRoot();
        assertEquals(expResult, result);
    }

    /**
     * Test of distance method, of class TaxonomicDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        TaxonomicDistance td = new TaxonomicDistance(instance);
        assertEquals(2.0, td.d("A", "F"), 1E-9);
        assertEquals(5.0, td.d("E", "F"), 1E-9);
    }
}