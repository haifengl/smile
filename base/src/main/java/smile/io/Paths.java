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

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Static methods that return a Path by converting a path string or URI.
 *
 * @author Haifeng Li
 */
public interface Paths {
    /** Smile home directory. */
    final String home = System.getProperty("smile.home", "base/src/test/resources/");
    /** Readonly file systems for resources. */
    final List<FileSystem> resourceFileSystems = new CopyOnWriteArrayList<>();

    /**
     * Returns the file path of a resource.
     * @param clazz the class to load the resource.
     * @param path the resource path.
     * @return the file path of the resource.
     */
    static Optional<Path> resource(Class<?> clazz, String path) {
        var url = clazz.getResource(path);
        if (url == null) {
            return Optional.empty();
        }

        try {
            var uri = url.toURI();
            try {
                return Optional.of(Path.of(uri));
            } catch (FileSystemNotFoundException e) {
                // This exception is expected if the filesystem for the JAR hasn't been created

                // Initialize a ZipFileSystem and DO NOT try-resource.
                // Otherwise, it will be closed for following file access.
                var fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                resourceFileSystems.add(fileSystem);
                return Optional.of(fileSystem.getPath(path));
            }
        } catch (URISyntaxException | IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the file path of a resource.
     * @param path the resource path.
     * @return the file path of the resource.
     */
    static Optional<Path> resource(String path) {
        for (var fs : resourceFileSystems) {
            var p = fs.getPath(path);
            if (Files.exists(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

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
     * Returns the file extension in lower case.
     * @param path the file path.
     * @return the file extension in lower case, or empty string if no extension is found.
     */
    static String getFileExtension(Path path) {
        Path file = path.getFileName();
        if (file == null) {
            return ""; // Handle cases where the path doesn't have a filename component
        }

        String name = file.toString();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < name.length() - 1) {
            return name.substring(lastDotIndex + 1).toLowerCase();
        }

        // Return empty string if no extension or file name starts/ends with a dot
        return "";
    }

    /**
     * Heuristically checks if a file is likely a binary file.
     * The method considers a file binary if it contains any null bytes
     * within the first 1024 bytes.
     *
     * @param path The file to check.
     * @return true if the file is likely binary, false otherwise.
     */
    static boolean isBinary(Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile());
            BufferedInputStream bis = new BufferedInputStream(fis)) {
            // Read up to 1024 bytes
            for (int b, count = 0; (b = bis.read()) != -1 && count < 1024; count++) {
                if (b == 0) { // Check for the null byte
                    return true;
                }
            }
        } catch (IOException _) {
            // If we can't read the file, we can't determine if it's binary.
            // So we return false.
        }
        return false;
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
