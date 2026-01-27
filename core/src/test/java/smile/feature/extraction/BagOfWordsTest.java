/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.io.IOException;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.io.Read;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BagOfWordsTest {
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    public BagOfWordsTest() {
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
    public void testUniquenessOfFeatures() {
        assertThrows(IllegalArgumentException.class, () -> {
            System.out.println("unique features");
            String[] features = {"crane", "sparrow", "hawk", "owl", "kiwi", "kiwi"};
            BagOfWords bag = new BagOfWords(tokenizer, features);
        });
    }

    @Test
    public void testFeature() throws IOException {
        System.out.println("feature");
        String[][] text = smile.io.Paths.getTestDataLines("text/movie.txt")
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

            assertEquals(data.size(), df.size());
            assertEquals(10, df.ncol());
            assertEquals(10, bag.features().length);
            assertEquals("--", bag.features()[0]);
            assertEquals("Union", bag.features()[9]);
            for (int i = 0; i < 10; i++) {
                var feature = bag.features()[i];
                System.out.println(feature);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
