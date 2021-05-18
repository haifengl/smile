/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data;

import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

import java.io.BufferedReader;
import java.util.Arrays;

/**
 *
 * @author Haifeng
 */
public class Colon {

    public static double[][] x;
    public static int[] y;

    static {
        try {
            BufferedReader reader = Paths.getTestDataReader("classification/colon.txt");
            y = Arrays.stream(reader.readLine().split(" ")).mapToInt(s -> Integer.parseInt(s) > 0 ? 1 : 0).toArray();

            x = new double[62][2000];
            for (int i = 0; i < 2000; i++) {
                String[] tokens = reader.readLine().split(" ");
                for (int j = 0; j < 62; j++) {
                    x[j][i] = Double.parseDouble(tokens[j]);
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to load 'Colon': " + ex);
            System.exit(-1);
        }
    }
}
