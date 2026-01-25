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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import smile.io.Paths;

/**
 * Colon cancer dataset. This dataset has gene expression samples that
 * were analyzed with an Affymetrix Oligonucleotide array complementary
 * to more than 6500 human genes. The data set contains 62 samples collected
 * from colon-cancer patients. Among them, 40 tumor biopsies are from tumors
 * (labeled as “negative”) and 22 normal (labeled as “positive”) biopsies are
 * from healthy parts of the colons of the same patients. Two thousand out
 * of around 6500 genes were selected based on the confidence in the measured
 * expression levels.
 *
 * @param x the sample data.
 * @param y the class label.
 * @author Haifeng Li
 */
public record ColonCancer(double[][] x, int[] y) {
    /**
     * Load built-in data.
     * @return the data.
     * @throws IOException when fails to read the file.
     */
    public static ColonCancer load() throws IOException {
        return load(Paths.getTestData("classification/colon.txt"));
    }

    /**
     * Load data from the given path.
     * @param path the data path.
     * @return the data.
     * @throws IOException when fails to read the file.
     */
    public static ColonCancer load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            int[] y = Arrays.stream(reader.readLine()
                    .split(" "))
                    .mapToInt(s -> Integer.parseInt(s) > 0 ? 1 : 0)
                    .toArray();

            double[][] x = new double[62][2000];
            for (int i = 0; i < 2000; i++) {
                String[] tokens = reader.readLine().split(" ");
                for (int j = 0; j < 62; j++) {
                    x[j][i] = Double.parseDouble(tokens[j]);
                }
            }
            return new ColonCancer(x, y);
        }
    }
}
