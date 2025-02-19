/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import smile.data.SparseDataset;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class LibsvmTest {
    
    public LibsvmTest() {
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
    public void testParseNG20() throws Exception {
        System.out.println("NG20");
        SparseDataset<Integer> train = Read.libsvm(smile.io.Paths.getTestData("libsvm/news20.dat"));
        SparseDataset<Integer> test  = Read.libsvm(smile.io.Paths.getTestData("libsvm/news20.t.dat"));

        assertEquals(15935, train.size());
        assertEquals(1,   train.get(0).y());
        assertEquals(0.0, train.get(0).x().get(0), 1E-7);
        assertEquals(0.0, train.get(0).x().get(1), 1E-7);
        assertEquals(2.0, train.get(0).x().get(196), 1E-7);
        assertEquals(3.0, train.get(0).x().get(320), 1E-7);
        assertEquals(0.0, train.get(0).x().get(20504), 1E-7);
        assertEquals(1.0, train.get(0).x().get(20505), 1E-7);
        assertEquals(1.0, train.get(0).x().get(20506), 1E-7);
        assertEquals(0.0, train.get(0).x().get(20507), 1E-7);

        int n = train.size() - 1;
        assertEquals(17,  train.get(n).y(), 16);
        assertEquals(1.0, train.get(n).x().get(0), 1E-7);
        assertEquals(0.0, train.get(n).x().get(1), 1E-7);
        assertEquals(1.0, train.get(n).x().get(9), 1E-7);
        assertEquals(0.0, train.get(n).x().get(10), 1E-7);
        assertEquals(0.0, train.get(n).x().get(57796), 1E-7);
        assertEquals(1.0, train.get(n).x().get(57797), 1E-7);
        assertEquals(0.0, train.get(n).x().get(57798), 1E-7);

        n = test.size();
        assertEquals(3993, test.size());
        assertEquals(2,  test.get(0).y());
        assertEquals(18, test.get(n-3).y());
        assertEquals(19, test.get(n-2).y());
        assertEquals(17, test.get(n-1).y());
    }

    @Test
    public void testParseGlass() throws Exception {
        System.out.println("glass");
        SparseDataset<Integer> train = Read.libsvm(smile.io.Paths.getTestData("libsvm/glass.txt"));

        assertEquals(214, train.size());
        assertEquals(9, train.get(0).x().size());
            
        assertEquals( 1,        train.get(0).y());
        assertEquals(-0.134323, train.get(0).x().get(0), 1E-7);
        assertEquals(-0.124812, train.get(0).x().get(1), 1E-7);
        assertEquals( 1.000000, train.get(0).x().get(2), 1E-7);
        assertEquals(-0.495327, train.get(0).x().get(3), 1E-7);
        assertEquals(-0.296429, train.get(0).x().get(4), 1E-7);
        assertEquals(-0.980676, train.get(0).x().get(5), 1E-7);
        assertEquals(-0.382900, train.get(0).x().get(6), 1E-7);
        assertEquals(-1.000000, train.get(0).x().get(7), 1E-7);
        assertEquals(-1.000000, train.get(0).x().get(8), 1E-7);
            
        assertEquals(7,          train.get(213).y());
        assertEquals(-0.4767340, train.get(213).x().get(0), 1E-7);
        assertEquals( 0.0526316, train.get(213).x().get(1), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(2), 1E-7);
        assertEquals( 0.1152650, train.get(213).x().get(3), 1E-7);
        assertEquals( 0.2678570, train.get(213).x().get(4), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(5), 1E-7);
        assertEquals(-0.4070630, train.get(213).x().get(6), 1E-7);
        assertEquals( 0.0603174, train.get(213).x().get(7), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(8), 1E-7);
    }
}
