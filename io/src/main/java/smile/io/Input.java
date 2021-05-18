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

package smile.io;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Static methods that return the InputStream/Reader of a file or URL.
 *
 * @author Haifeng Li
 */
public interface Input {
    /**
     * Returns the reader of a file path or URI.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the file reader.
     */
    static BufferedReader reader(String path) throws IOException, URISyntaxException {
        return new BufferedReader(new InputStreamReader(stream(path)));
    }

    /**
     * Returns the reader of a file path or URI.
     * @param path the input file path.
     * @param charset the charset of file.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the file reader.
     */
    static BufferedReader reader(String path, Charset charset) throws IOException, URISyntaxException {
        return new BufferedReader(new InputStreamReader(stream(path), charset));
    }

    /**
     * Returns the input stream of a file path or URI.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the file input stream.
     */
    static InputStream stream(String path) throws IOException, URISyntaxException {
        // Windows file path
        if (path.matches("[a-zA-Z]:\\\\[\\\\\\S|*\\S]?.*")) {
            return Files.newInputStream(Paths.get(path));
        }

        URI uri = new URI(path);
        String scheme = uri.getScheme();
        // If scheme is single character, assume it is the drive letter in Windows.
        if (scheme == null || scheme.length() < 2) {
            return Files.newInputStream(Paths.get(path));
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return Files.newInputStream(Paths.get(uri.getPath()));
        }

        // http, ftp, ...
        return uri.toURL().openStream();
    }
}
