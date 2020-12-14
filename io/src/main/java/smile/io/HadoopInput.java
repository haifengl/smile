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
import java.text.ParseException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;

/**
 * Static methods that return the InputStream/Reader of a HDFS/S3.
 * Local files, HTTP and FTP URLs are supported too.
 *
 * @author Haifeng Li
 */
public interface HadoopInput {
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
     * Returns the reader of a file path or URI.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the file input stream.
     */
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

    /**
     * Returns the Parquet's InputFile instance of a file path or URI.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return Parquet's InputFile.
     */
    static InputFile file(String path) throws IOException, URISyntaxException {
        URI uri = new URI(path);
        if (uri.getScheme() == null) return new LocalInputFile(Paths.get(path));

        switch (uri.getScheme().toLowerCase()) {
            case "file":
                return new LocalInputFile(Paths.get(path));

            case "s3":
            case "s3a":
            case "s3n":
            case "hdfs":
                Configuration conf = new Configuration();
                FileSystem fs = FileSystem.get(conf); // initialize file system
                return HadoopInputFile.fromPath(new org.apache.hadoop.fs.Path(path), conf);

            default: // http, ftp, ...
                throw new IllegalArgumentException("Unsupported URI schema for Parquet files: " + path);
        }
    }
}
