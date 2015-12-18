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

package smile.math.distance;

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
public class EditDistanceTest {
    String H1N1 = "ATGGAGAGAATAAAAGAACTGAGAGATCTAATGTCGCAGTCCCGCACTCGCGAGATACTCACTAAGACCACTGTGGACCATATGGCCATAATCAAAAAGTACACATCAGGAAGGCAAGAGAAGAACCCCGCACTCAGAATGAAGTGGATGATGGCAATGAGATACCCAATTACAGCAGACAAGAGAATAATGGACATGATTCCAGAGAGGAATGAACAAGGACAAACCCTCTGGAGCAAAACAAACGATGCTGGATCAGACCGAGTGATGGTATCACCTCTGGCCGTAACATGGTGGAATAGGAATGGCCCAACAACAAGTACAGTTCATTACCCTAAGGTATATAAAACTTATTTCGAAAAGGTCGAAAGGTTGAAACATGGTACCTTCGGCCCTGTCCACTTCAGAAATCAAGTTAAAATAAGGAGGAGAGTTGATACAAACCCTGGCCATGCAGATCTCAGTGCCAAGGAGGCACAGGATGTGATTATGGAAGTTGTTTTCCCAAATGAAGTGGGGGCAAGAATACTGACATCAGAGTCACAGCTGGCAATAACAAAAGAGAAGAAAGAAGAGCTCCAGGATTGTAAAATTGCTCCCTTGATGGTGGCGTACATGCTAGAAAGAGAATTGGTCCGTAAAACAAGGTTTCTCCCAGTAGCCGGCGGAACAGGCAGTGTTTATATTGAAGTGTTGCACTTAACCCAAGGGACGTGCTGGGAGCAGATGTACACTCCAGGAGGAGAAGTGAGAAATGATGATGTTGACCAAAGTTTGATTATCGCTGCTAGAAACATAGTAAGAAGAGCAGCAGTGTCAGCAGACCCATTAGCATCTCTCTTGGAAATGTGCCACAGCACACAGATTGGAGGAGTAAGGATGGTGGACATCCTTAGACAGAATCCAACTGAGGAACAAGCCGTAGACATATGCAAGGCAGCAATAGGGTTGAGGATTAGCTCATCTTTCAGTTTTGGTGGGTTCACTTTCAAAAGGACAAGCGGATCATCAGTCAAGAAAGAAGAAGAAGTGCTAACGGGCAACCTCCAAACACTGAAAATAAGAGTACATGAAGGGTATGAAGAATTCACAATGGTTGGGAGAAGAGCAACAGCTATTCTCAGAAAGGCAACCAGGAGATTGATCCAGTTGATAGTAAGCGGGAGAGACGAGCAGTCAATTGCTGAGGCAATAATTGTGGCCATGGTATTCTCACAAGAGGATTGCATGATCAAGGCAGTTAGGGGCGATCTGAACTTTGTCAATAGGGCAAACCAGCGACTGAACCCCATGCACCAACTCTTGAGGCATTTCCAAAAAGATGCAAAAGTGCTTTTCCAGAACTGGGGAATTGAATCCATCGACAATGTGATGGGAATGATCGGAATACTGCCCGACATGACCCCAAGCACGGAGATGTCGCTGAGAGGGATAAGAGTCAGCAAAATGGGAGTAGATGAATACTCCAGCACGGAGAGAGTGGTAGTGAGTATTGACCGATTTTTAAGGGTTAGAGATCAAAGAGGGAACGTACTATTGTCTCCCGAAGAAGTCAGTGAAACGCAAGGAACTGAGAAGTTGACAATAACTTATTCGTCATCAATGATGTGGGAGATCAATGGCCCTGAGTCAGTGCTAGTCAACACTTATCAATGGATAATCAGGAACTGGGAAATTGTGAAAATTCAATGGTCACAAGATCCCACAATGCTATACAACAAAATGGAATTTGAACCATTTCAGTCTCTTGTCCCTAAGGCAACCAGAAGCCGGTACAGTGGATTCGTAAGGACACTGTTCCAGCAAATGCGGGATGTGCTTGGGACATTTGACACTGTCCAAATAATAAAACTTCTCCCCTTTGCTGCTGCTCCACCAGAACAGAGTAGGATGCAATTTTCCTCATTGACTGTGAATGTGAGAGGATCAGGGTTGAGGATACTGATAAGAGGCAATTCTCCAGTATTCAATTACAACAAGGCAACCAAACGACTTACAGTTCTTGGAAAGGATGCAGGTGCATTGACTGAAGATCCAGATGAAGGCACATCTGGGGTGGAGTCTGCTGTCCTGAGAGGATTTCTCATTTTGGGCAAAGAAGACAAGAGATATGGCCCAGCATTAAGCATCAATGAACTGAGCAATCTTGCAAAAGGAGAGAAGGCTAATGTGCTAATTGGGCAAGGGGACGTAGTGTTGGTAATGAAACGAAAACGGGACTCTAGCATACTTACTGACAGCCAGACAGCGACCAAAAGAATTCGGATGGCCATCAATTAG";
    String H1N5 = "AGCGAAAGCAGGTCAAATATATTCAATATGGAGAGAATAAAAGAACTAAGAGATCTAATGTCACAGTCCCGCACCCGCGAGATACTCACCAAAACCACTGTGGACCACATGGCCATAATCAAAAAATACACATCAGGAAGGCAAGAGAAGAACCCCGCACTCAGGATGAAGTGGATGATGGCAATGAAATATCCAATTACGGCAGATAAGAGAATAATGGAAATGATTCCTGAAAGGAATGAACAAGGACAAACCCTCTGGAGCAAAACAAACGATGCCGGCTCAGACCGAGTGATGGTATCACCTCTGGCCGTAACATGGTGGAATAGGAATGGACCAACAACAAGTACAGTCCACTACCCAAAGGTATATAAAACTTACTTCGAAAAAGTCGAAAGGTTGAAACACGGGACCTTTGGCCCTGTCCACTTCAGAAATCAAGTTAAGATAAGACGGAGGGTTGACATAAACCCTGGCCACGCAGACCTCAGTGCCAAAGAGGCACAGGATGTAATCATGGAAGTTGTTTTCCCAAATGAAGTGGGAGCTAGAATACTAACATCGGAGTCACAACTGACAATAACAAAAGAGAAGAAGGAAGAACTCCAGGACTGTAAAATTGCCCCCTTGATGGTAGCATACATGCTAGAAAGAGAGTTGGTCCGCAAAACGAGGTTCCTCCCAGTGGCTGGTGGAACAAGCAGTGTCTATATTGAGGTGTTGCATTTAACCCAGGGGACATGCTGGGAGCAGATGTACACTCCAGGAGGGGAAGTGAGAAATGATGATGTTGACCAAAGCTTGATTATCGCTGCCAGGAACATAGTAAGAAGAGCAACGGTATCAGCAGACCCACTAGCATCTCTATTGGAGATGTGCCACAGCACACAGATTGGGGGAATAAGGATGGTAGACATCCTTCGGCAAAATCCAACAGAGGAACAAGCCGTGGACATATGCAAGGCAGCAATGGGATTGAGGATTAGCTCATCTTTCAGCTTTGGTGGATTCACTTTCAAAAGAACAAGCGGGTCGTCAGTTAAGAGAGAAGAAGAAGTGCTTACGGGCAACCTTCAAACATTGAAAATAAGAGTACATGAGGGGTATGAAGAGTTCACAATGGTTGGGAGAAGAGCAACAGCTATTCTCAGAAAAGCAACCAGGAGATTGATCCAGCTAATAGTAAGTGGGAGAGACGAGCAGTCAATTGCTGAAGCAATAATTGTGGCCATGGTATTTTCACAAGAGGATTGCATGATCAAGGCAGTTCGGGGTGATCTGAACTTTGTCAATAGGGCAAACCAGCGACTGAACCCCATGCATCAACTCTTGAGACACTTCCAAAAGGATGCAAAAGTGCTTTTCCAAAACTGGGGAATTGAACCCATTGACAATGTGATGGGAATGATCGGAATATTGCCCGACATGACCCCAAGTACTGAAATGTCGCTGAGGGGAATAAGAGTCAGCAAAATGGGAGTAGATGAATACTCCAGCACAGAGAGGGTGGTGGTGAGCATTGACCGATTTTTAAGGGTTCGGGATCAAAGGGGAAACGTACTATTGTCACCCGAAGAAGTCAGCGAGACACAAGGAACGGAGAAGCTGACGATAACTTATTCGTCATCAATGATGTGGGAGATCAATGGTCCTGAGTCGGTGTTGGTCAATACTTATCAGTGGATCATAAGGAACTGGGAGACTGTGAAAATTCAATGGTCACAGGATCCCACAATGTTATATAATAAGATGGAATTCGAGCCATTTCAGTCTCTGGTCCCTAAGGCAGCCAGAGGTCAATACAGCGGATTCGTGAGGACACTGTTCCAGCAGATGCGGGATGTGCTTGGAACATTTGACACTGTTCAGATAATAAAACTTCTCCCCTTTGCTGCTGCTCCACCAGAACAGAGTAGGATGCAGTTCTCCTCCCTGACTGTGAATGTAAGAGGATCAGGAATGAGGATACTGGTAAGAGGCAATTCTCCAGTGTTCAATTACAACAAGGCCACCAAGAGGCTTACAGTCCTCGGGAAAGATGCAGGTGCATTGACCGAAGATCCAGATGAAGGTACAGCTGGAGTGGAGTCTGCTGTTCTAAGAGGATTCCTCATTTTGGGCAAAGAAGACAAGAGATATGGCCCAGCATTAAGCATCAATGAGCTGAGCAATCTTGCAAAAGGAGAGAAGGCTAATGTGCTAATTGGGCAAGGAGACGTGGTGTTGGTAATGAAACGGAAACGGGACTCTAGCATACTTACTGACAGCCAGACAGCGACCAAAAGAATTCGGATGGCCATCAATTAGTGTCGAATTGTTTAAAAACGACCTTGTTTCTACT";

    public EditDistanceTest() {
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
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");

        String x = "Levenshtein";
        String y = "Laeveshxtin";
        String z = "Laeveshetin";

        for (int i = 0; i < 100000; i++)
            EditDistance.levenshtein(x, y);
        //    EditDistance.levenshtein("abcdefghi", "jklmnopqrst");

        assertEquals(0, EditDistance.levenshtein(x, x));
        assertEquals(4, EditDistance.levenshtein(x, y));
        assertEquals(4, EditDistance.levenshtein(x, z));

        assertEquals(5, EditDistance.levenshtein("adcroft", "addessi"));
        assertEquals(3, EditDistance.levenshtein("baird", "baisden"));
        assertEquals(2, EditDistance.levenshtein("boggan", "boggs"));
        assertEquals(5, EditDistance.levenshtein("clayton", "cleary"));
        assertEquals(4, EditDistance.levenshtein("dybas", "dyckman"));
        assertEquals(4, EditDistance.levenshtein("emineth", "emmert"));
        assertEquals(4, EditDistance.levenshtein("galante", "galicki"));
        assertEquals(1, EditDistance.levenshtein("hardin", "harding"));
        assertEquals(2, EditDistance.levenshtein("kehoe", "kehr"));
        assertEquals(5, EditDistance.levenshtein("lowry", "lubarsky"));
        assertEquals(3, EditDistance.levenshtein("magallan", "magana"));
        assertEquals(1, EditDistance.levenshtein("mayo", "mays"));
        assertEquals(4, EditDistance.levenshtein("moeny", "moffett"));
        assertEquals(2, EditDistance.levenshtein("pare", "parent"));
        assertEquals(2, EditDistance.levenshtein("ramey", "ramfrey"));

        assertEquals(0, EditDistance.damerau(x, x));
        assertEquals(4, EditDistance.damerau(x, y));
        assertEquals(3, EditDistance.damerau(x, z));

        assertEquals(5, EditDistance.damerau("adcroft", "addessi"));
        assertEquals(3, EditDistance.damerau("baird", "baisden"));
        assertEquals(2, EditDistance.damerau("boggan", "boggs"));
        assertEquals(5, EditDistance.damerau("clayton", "cleary"));
        assertEquals(4, EditDistance.damerau("dybas", "dyckman"));
        assertEquals(4, EditDistance.damerau("emineth", "emmert"));
        assertEquals(4, EditDistance.damerau("galante", "galicki"));
        assertEquals(1, EditDistance.damerau("hardin", "harding"));
        assertEquals(2, EditDistance.damerau("kehoe", "kehr"));
        assertEquals(5, EditDistance.damerau("lowry", "lubarsky"));
        assertEquals(3, EditDistance.damerau("magallan", "magana"));
        assertEquals(1, EditDistance.damerau("mayo", "mays"));
        assertEquals(4, EditDistance.damerau("moeny", "moffett"));
        assertEquals(2, EditDistance.damerau("pare", "parent"));
        assertEquals(2, EditDistance.damerau("ramey", "ramfrey"));
    }

    /**
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance2() {
        System.out.println("distance");

        String x = "Levenshtein";
        String y = "Laeveshxtin";
        String z = "Laeveshetin";

        EditDistance edit = new EditDistance(20, false);
        for (int i = 0; i < 100000; i++)
            edit.d(x, y);
        //    edit.d("abcdefghi", "jklmnopqrst");

        assertEquals(0, edit.d(x, x), 1E-7);
        assertEquals(4, edit.d(x, y), 1E-7);
        assertEquals(4, edit.d(x, z), 1E-7);
        
        assertEquals(5, edit.d("adcroft", "addessi"), 1E-7);
        assertEquals(3, edit.d("baird", "baisden"), 1E-7);
        assertEquals(2, edit.d("boggan", "boggs"), 1E-7);
        assertEquals(5, edit.d("clayton", "cleary"), 1E-7);
        assertEquals(4, edit.d("dybas", "dyckman"), 1E-7);
        assertEquals(4, edit.d("emineth", "emmert"), 1E-7);
        assertEquals(4, edit.d("galante", "galicki"), 1E-7);
        assertEquals(1, edit.d("hardin", "harding"), 1E-7);
        assertEquals(2, edit.d("kehoe", "kehr"), 1E-7);
        assertEquals(5, edit.d("lowry", "lubarsky"), 1E-7);
        assertEquals(3, edit.d("magallan", "magana"), 1E-7);
        assertEquals(1, edit.d("mayo", "mays"), 1E-7);
        assertEquals(4, edit.d("moeny", "moffett"), 1E-7);
        assertEquals(2, edit.d("pare", "parent"), 1E-7);
        assertEquals(2, edit.d("ramey", "ramfrey"), 1E-7);

        edit = new EditDistance(20, true);
        assertEquals(0, edit.d(x, x), 1E-7);
        assertEquals(4, edit.d(x, y), 1E-7);
        assertEquals(3, edit.d(x, z), 1E-7);

        assertEquals(5, edit.d("adcroft", "addessi"), 1E-7);
        assertEquals(3, edit.d("baird", "baisden"), 1E-7);
        assertEquals(2, edit.d("boggan", "boggs"), 1E-7);
        assertEquals(6, edit.d("lcayton", "cleary"), 1E-7);
        assertEquals(5, edit.d("ydbas", "dyckman"), 1E-7);
        assertEquals(4, edit.d("emineth", "emmert"), 1E-7);
        assertEquals(4, edit.d("galante", "galicki"), 1E-7);
        assertEquals(1, edit.d("hardin", "harding"), 1E-7);
        assertEquals(2, edit.d("kehoe", "kehr"), 1E-7);
        assertEquals(5, edit.d("lowry", "lubarsky"), 1E-7);
        assertEquals(3, edit.d("magallan", "magana"), 1E-7);
        assertEquals(1, edit.d("mayo", "mays"), 1E-7);
        assertEquals(4, edit.d("moeny", "moffett"), 1E-7);
        assertEquals(2, edit.d("pare", "parent"), 1E-7);
        assertEquals(2, edit.d("ramey", "ramfrey"), 1E-7);
    }

    /**
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance3() {
        System.out.println("distance");
        System.out.println(EditDistance.levenshtein(H1N1, H1N5));
    }

    EditDistance ed = new EditDistance(Math.max(H1N1.length(), H1N5.length()));

    /**
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance4() {
        System.out.println("distance");
        System.out.println(ed.d(H1N1, H1N5));
    }

    /**
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance5() {
        System.out.println("distance");
        System.out.println(EditDistance.damerau(H1N1, H1N5));
    }

    EditDistance ed2 = new EditDistance(Math.max(H1N1.length(), H1N5.length()), true);

    /**
     * Test of distance method, of class EditDistance.
     */
    @Test
    public void testDistance6() {
        System.out.println("distance");
        System.out.println(ed2.d(H1N1, H1N5));
    }
}