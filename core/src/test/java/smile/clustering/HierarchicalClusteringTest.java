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
package smile.clustering;

import smile.clustering.linkage.SingleLinkage;
import smile.clustering.linkage.WPGMCLinkage;
import smile.clustering.linkage.WardLinkage;
import smile.clustering.linkage.UPGMCLinkage;
import smile.clustering.linkage.WPGMALinkage;
import smile.clustering.linkage.UPGMALinkage;
import smile.clustering.linkage.CompleteLinkage;
import smile.math.Math;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
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
public class HierarchicalClusteringTest {
    
    public HierarchicalClusteringTest() {
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
     * Test of learn method, of class GMeans.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            
            int n = x.length;
            double[][] proximity = new double[n][];
            for (int i = 0; i < n; i++) {
                proximity[i] = new double[i + 1];
                for (int j = 0; j < i; j++) {
                    proximity[i][j] = Math.distance(x[i], x[j]);
                }
            }

            AdjustedRandIndex ari = new AdjustedRandIndex();
            RandIndex rand = new RandIndex();
            
            HierarchicalClustering hc = new HierarchicalClustering(new SingleLinkage(proximity));
            int[] label = hc.partition(10);
            double r = rand.measure(y, label);
            double r2 = ari.measure(y, label);
            System.out.format("SingleLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.1);
            
            hc = new HierarchicalClustering(new CompleteLinkage(proximity));
            label = hc.partition(10);
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("CompleteLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.75);
            
            hc = new HierarchicalClustering(new UPGMALinkage(proximity));
            label = hc.partition(10);
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("UPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.1);
            
            hc = new HierarchicalClustering(new WPGMALinkage(proximity));
            label = hc.partition(10);            
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("WPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.2);
            
            hc = new HierarchicalClustering(new UPGMCLinkage(proximity));
            label = hc.partition(10);            
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("UPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.1);
            
            hc = new HierarchicalClustering(new WPGMCLinkage(proximity));
            label = hc.partition(10);
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("WPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.1);
            
            hc = new HierarchicalClustering(new WardLinkage(proximity));
            label = hc.partition(10);
            r = rand.measure(y, label);
            r2 = ari.measure(y, label);
            System.out.format("Ward rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.9);
            assertTrue(r2 > 0.5);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
