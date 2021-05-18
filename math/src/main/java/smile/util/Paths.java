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

    /**
     * Get the file path of a test sample dataset.
     * @param path the path strings to be joined to form the path.
     * @return the file path to the test data.
     */
    static Path getTestData(String... path) {
        return java.nio.file.Paths.get(home + "/data", path);
    }

    /**
     * Returns the reader of a test data.
     * @param path the path strings to be joined to form the path.
     * @return the reader of the test data.
     * @throws IOException when fails to create the reader.
     */
    static BufferedReader getTestDataReader(String... path) throws IOException {
        return java.nio.file.Files.newBufferedReader(getTestData(path));
    }

    /**
     * Returns the reader of a test data.
     * @param path the path strings to be joined to form the path.
     * @return the file lines of test data.
     * @throws IOException when fails to read the file.
     */
    static Stream<String> getTestDataLines(String... path) throws IOException {
        return java.nio.file.Files.lines(getTestData(path));
    }
}
