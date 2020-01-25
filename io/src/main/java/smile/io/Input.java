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

package smile.io;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;

/**
 * Static methods that return the InputStream/Reader of a file or URL.
 *
 * @author Haifeng Li
 */
public interface Input {
    /** Returns the reader of a file path or URI. */
    static BufferedReader reader(String path) throws IOException, URISyntaxException {
        return new BufferedReader(new InputStreamReader(stream(path)));
    }

    /** Returns the reader of a file path or URI. */
    static BufferedReader reader(String path, Charset charset) throws IOException, URISyntaxException {
        return new BufferedReader(new InputStreamReader(stream(path), charset));
    }

    /** Returns the reader of a file path or URI. */
    static InputStream stream(String path) throws IOException, URISyntaxException {
        URI uri = new URI(path);
        if (uri.getScheme() == null) return Files.newInputStream(Paths.get(path));

        switch (uri.getScheme().toLowerCase()) {
            case "file":
                return Files.newInputStream(Paths.get(path));

            case "s3":
            case "s3a":
            case "s3n":
            case "hdfs":
                Configuration conf = new Configuration();
                FileSystem fs = FileSystem.get(conf);
                return fs.open(new org.apache.hadoop.fs.Path(path));

            default: // http, ftp, ...
                return uri.toURL().openStream();
        }
    }
}
