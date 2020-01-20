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

package smile.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Static methods that return a Path by converting a path string or URI.
 *
 * @author Haifeng Li
 */
public interface Paths {
    /** Smile home directory. */
    String home = System.getProperty("smile.home", "shell/src/universal/");

    /** Get the file path of a test sample dataset. */
    static Path getTestData(String... path) {
        return java.nio.file.Paths.get(home + "/data", path);
    }

    /** Returns the reader of a test data. */
    static BufferedReader getTestDataReader(String... path) throws IOException {
        return java.nio.file.Files.newBufferedReader(getTestData(path));
    }

    /** Returns the reader of a test data. */
    static Stream<String> getTestDataLines(String... path) throws IOException {
        return java.nio.file.Files.lines(getTestData(path));
    }
}
