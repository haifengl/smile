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

package smile.data;

import java.io.BufferedReader;
import smile.feature.Bag;

/**
 *
 * @author Haifeng
 */
public class Movie {

    public static final String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws", "enjoyable", "funniest",
            "loved", "amazing", "favorite", "perfect", "poor", "worse", "horrible",
            "disappointing", "disappointment"
    };

    public static final String[][] doc = new String[2000][];
    public static final int[][] x = new int[doc.length][];
    public static final int[] y = new int[doc.length];

    static {
        try (BufferedReader input = smile.util.Paths.getTestDataReader("text/movie.txt")) {
            for (int i = 0; i < x.length; i++) {
                String[] words = input.readLine().trim().split("\\s+");

                if (words[0].equalsIgnoreCase("pos")) {
                    y[i] = 1;
                } else if (words[0].equalsIgnoreCase("neg")) {
                    y[i] = 0;
                } else {
                    System.err.println("Invalid class label: " + words[0]);
                }

                doc[i] = new String[words.length - 1];
                System.arraycopy(words, 1, doc[i], 0, doc[i].length);
            }

            Bag bag = new Bag(feature);
            for (int i = 0; i < x.length; i++) {
                x[i] = bag.apply(doc[i]);
            }
        } catch (Exception ex) {
            System.err.println("Failed to load 'movie': " + ex);
            System.exit(-1);
        }
    }
}
