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
package smile.association;

import java.io.IOException;
import java.util.stream.Stream;

/**
 *
 * @author Haifeng Li
 */
public interface ItemSetTestData {

    static Stream<int[]> read(String path) {
        try {
            return smile.io.Paths.getTestDataLines(path)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> {
                        String[] s = line.split(" ");

                        int[] basket = new int[s.length];
                        for (int i = 0; i < s.length; i++) {
                            basket[i] = Integer.parseInt(s[i]);
                        }
                        return basket;
                    });
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return Stream.empty();
    }
}
