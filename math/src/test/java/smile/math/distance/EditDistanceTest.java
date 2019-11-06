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

    @Test
    public void testStaticMethods() {
        System.out.println("static methods");

        String x = "Levenshtein";
        String y = "Laeveshxtin";
        String z = "Laeveshetin";

        assertEquals(0, EditDistance.levenshtein(x, x));
        assertEquals(4, EditDistance.levenshtein(x, y));
        assertEquals(4, EditDistance.levenshtein(x, z));

        assertEquals(2, EditDistance.levenshtein("act", "cat"));
        assertEquals(2, EditDistance.levenshtein("pat", "pta"));
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

        assertEquals(1, EditDistance.damerau("act", "cat"));
        assertEquals(1, EditDistance.damerau("pat", "pta"));
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

    @Test
    public void testUnitCost() {
        System.out.println("unit cost");

        String x = "Levenshtein";
        String y = "Laeveshxtin";
        String z = "Laeveshetin";

        EditDistance edit = new EditDistance(20, false);

        assertEquals(0, edit.d(x, x), 1E-7);
        assertEquals(4, edit.d(x, y), 1E-7);
        assertEquals(4, edit.d(x, z), 1E-7);

        assertEquals(2, edit.d("act", "cat"), 1E-7);
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

        assertEquals(1, edit.d("act", "cat"), 1E-7);
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

    @Test
    public void testPlainLevenshteinSpeedTest() {
        System.out.println("Levenshtein speed test");
        for (int i = 0; i < 100; i++) {
            EditDistance.levenshtein(H1N1, H1N5);
        }
    }

    @Test
    public void testLevenshteinSpeedTest() {
        System.out.println("Advanced Levenshtein speed test");
        EditDistance edit = new EditDistance(Math.max(H1N1.length(), H1N5.length()));
        for (int i = 0; i < 100; i++) {
            edit.d(H1N1, H1N5);
        }
    }

    @Test
    public void testPlainDamerauSpeedTest() {
        System.out.println("Plain Damerau speed test");
        for (int i = 0; i < 100; i++) {
            EditDistance.damerau(H1N1, H1N5);
        }
    }

    @Test
    public void testDamerauSpeedTest() {
        System.out.println("Advanced Damerau speed test");
        EditDistance edit = new EditDistance(Math.max(H1N1.length(), H1N5.length()), true);
        for (int i = 0; i < 100; i++) {
            edit.d(H1N1, H1N5);
        }
    }
}