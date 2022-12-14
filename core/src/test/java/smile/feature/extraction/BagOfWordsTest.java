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

package smile.feature.extraction;

import java.io.IOException;
import java.util.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.io.Read;
import smile.util.Paths;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BagOfWordsTest {
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    public BagOfWordsTest() {
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

    @Test(expected = IllegalArgumentException.class)
    public void testUniquenessOfFeatures() {
        System.out.println("unique features");
        String[] features = {"crane", "sparrow", "hawk", "owl", "kiwi", "kiwi"};
        BagOfWords bag = new BagOfWords(tokenizer, features);
    }

    @Test
    public void testFeature() throws IOException {
        System.out.println("feature");
        String[][] text = smile.util.Paths.getTestDataLines("text/movie.txt")
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+", 2))
                .toArray(String[][]::new);

        String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws"
        };

        BagOfWords bag = new BagOfWords(tokenizer, feature);
        
        int[][] x = new int[text.length][];
        for (int i = 0; i < text.length; i++) {
            x[i] = bag.apply(text[i][1]);
            assertEquals(feature.length, x[i].length);
        }
        
        assertEquals(1, x[0][15]);
    }

    @Test
    public void testFit() throws IOException {
        System.out.println("fit");
        try {
            DataFrame data = Read.arff(Paths.getTestData("weka/string.arff"));
            System.out.println(data);

            BagOfWords bag = BagOfWords.fit(data, tokenizer, 10,"LCSH");
            DataFrame df = bag.apply(data);
            System.out.println(df);

            assertEquals(data.nrow(), df.nrow());
            assertEquals(10, df.ncol());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
