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

package smile.feature;

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;

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
public class BagTest {
    
    public BagTest() {
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
    /**
     * Test of the uniqueness of features in the class Bag
     */
    public void testUniquenessOfFeatures() {
        System.out.println("unique features");
        String[] featuresForBirdStories = {"crane", "sparrow", "hawk", "owl", "kiwi"};
        String[] featuresForBuildingStories = {"truck", "concrete", "foundation", "steel", "crane"};
        String testMessage = "This story is about a crane and a sparrow";

        ArrayList<String> mergedFeatureLists = new ArrayList<>();
        mergedFeatureLists.addAll(Arrays.asList(featuresForBirdStories));
        mergedFeatureLists.addAll(Arrays.asList(featuresForBuildingStories));

        Bag<String> bag = new Bag<>(mergedFeatureLists.toArray(new String[featuresForBirdStories.length + featuresForBuildingStories.length]));

        double[] result = bag.feature(testMessage.split(" "));
        assertEquals(9, result.length);
    }

    /**
     * Test of feature method, of class Bag.
     */
    @Test
    public void testFeature() {
        System.out.println("feature");
        String[][] text = new String[2000][];

        try(BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("text/movie.txt")) {
            for (int i = 0; i < text.length; i++) {
                String[] words = input.readLine().trim().split("\\s+");
                text[i] = words;
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

        String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws"
        };
        
        Bag<String> bag = new Bag<>(feature);
        
        double[][] x = new double[text.length][];
        for (int i = 0; i < text.length; i++) {
            x[i] = bag.feature(text[i]);
            assertEquals(feature.length, x[i].length);
        }
        
        assertEquals(1.0, x[0][15], 1E-7);
    }
}
