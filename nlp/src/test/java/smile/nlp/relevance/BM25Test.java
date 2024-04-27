/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp.relevance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class BM25Test {

    public BM25Test() {
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

    /**
     * Test of rank method, of class BM25.
     */
    @Test
    public void testRank() {
        System.out.println("rank");
        int freq = 3;
        int docSize = 100;
        int avgDocSize = 150;
        int N = 10000000;
        int n = 1000;
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        double expResult = 18.419681;
        double result = instance.score(freq, docSize, avgDocSize, N, n);
        assertEquals(expResult, result, 1E-6);
    }
}