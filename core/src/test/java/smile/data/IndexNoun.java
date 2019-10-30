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

/**
 *
 * @author Haifeng
 */
public class IndexNoun {

    public static String[] words;

    static {
        try (BufferedReader input = smile.util.Paths.getTestDataReader("neighbor/index.noun")) {
            words = input.lines().filter(line -> !line.startsWith(" "))
                    .map(line -> line.split("\\s")[0].replace('_', ' '))
                    .toArray(String[]::new);
        } catch (Exception ex) {
            System.err.println("Failed to load 'index.noun': " + ex);
            System.exit(-1);
        }
    }
}
