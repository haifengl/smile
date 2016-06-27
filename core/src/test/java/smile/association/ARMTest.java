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
package smile.association;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import smile.math.Math;

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

    /**
     * Test of learn method, of class ARM.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");
        ARM instance = new ARM(itemsets, 3);
        instance.learn(0.5, System.out);
        List<AssociationRule> rules = instance.learn(0.5);
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

    /**
     * Test of learn method, of class ARM.
     */
    @Test
    public void testLearnPima() {
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

        int n = Math.max(data);
        System.out.format("%d transactions, %d items%n", data.length, n);
        
        ARM instance = new ARM(data, 20);
        long numRules = instance.learn(0.9, System.out);
        System.out.format("%d association rules discovered%n", numRules);
        assertEquals(6803, numRules);
        assertEquals(6803, instance.learn(0.9).size());
    }

    /**
     * Test of learn method, of class ARM.
     */
    @Test
    public void testLearnKosarak() {
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

        int n = Math.max(data);
        System.out.format("%d transactions, %d items%n", data.length, n);
        
        ARM instance = new ARM(data, 0.003);
        long numRules = instance.learn(0.5, System.out);
        System.out.format("%d association rules discovered%n", numRules);
        assertEquals(17932, numRules);
    }
}
