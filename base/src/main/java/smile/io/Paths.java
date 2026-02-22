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
package smile.io;

import java.io.BufferedReader;
import java.io.File;
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
    String home = System.getProperty("smile.home", "base/src/test/resources/");

    /**
     * Returns the file name without extension.
     * @param path the file path.
     * @return the file name without extension.
     */
    static String getFileName(Path path) {
        Path file = path.getFileName();
        if (file == null) {
            return ""; // Handle cases where the path doesn't have a filename component
        }

        String name = file.toString();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0) { // Ensures the file is not a hidden file like ".gitignore" (where pos=0)
            return name.substring(0, lastDotIndex);
        } else {
            return name; // Returns the original name if no extension is found
        }
    }

    /**
     * Get the file path of a test sample dataset.
     * @param path the path strings to be joined to form the path.
     * @return the file path to the test data.
     */
    static Path getTestData(String... path) {
        return Path.of(home + File.separator + "data", path);
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
