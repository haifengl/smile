/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.data.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;

import smile.data.SparseDataset;

/**
 * Parser for spare dataset in coordinate triple tuple list format.
 * Coordinate file stores a list of (row, column, value) tuples:
 * <pre>
 * instanceID attributeID value
 * instanceID attributeID value
 * instanceID attributeID value
 * instanceID attributeID value
 * ...
 * instanceID attributeID value
 * instanceID attributeID value
 * instanceID attributeID value
 * </pre>
 * Ideally, the entries are sorted (by row index, then column index) to
 * improve random access times. This format is good for incremental matrix
 * construction.
 * <p>
 * Optionally, there may be 2 header lines
 * <pre>
 * D    // The number of instances
 * W    // The number of attributes
 * </pre>
 * or 3 header lines
 * <pre>
 * D    // The number of instances
 * W    // The number of attributes
 * N    // The total number of nonzero items in the dataset.
 * </pre>
 * These header lines will be ignored.
 * 
 * @author Haifeng Li
 */
public class SparseDatasetParser {
    /**
     * The starting index of array in the data.
     */
    private int arrayIndexOrigin = 0;
    
    /**
     * Constructor.
     */
    public SparseDatasetParser() {
    }

    /**
     * Constructor.
     * @param arrayIndexOrigin the starting index of array. By default, it is
     * 0 as in C/C++ and Java. But it could be 1 to parse data produced
     * by other programming language such as Fortran.
     */
    public SparseDatasetParser(int arrayIndexOrigin) {
        this.arrayIndexOrigin = arrayIndexOrigin;
    }

    /**
     * Parse a sparse dataset from given URI.
     * @throws java.io.IOException
     */
    public SparseDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a sparse dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a sparse dataset from given file.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a sparse dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a sparse dataset from given file.
     * @throws java.io.IOException
     */
    public SparseDataset parse(File file) throws IOException, ParseException {
        String name = file.getPath();
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a sparse dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a sparse dataset from an input stream.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public SparseDataset parse(InputStream stream) throws IOException, ParseException {
        return parse("Sparse Dataset", stream);
    }
    
    /**
     * Parse a sparse dataset from an input stream.
     * @param name the name of dataset.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, InputStream stream) throws IOException, ParseException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        try {
            // process header
            int nrow = 1;
            String line = reader.readLine();
            for (; nrow <= 3 && line != null; nrow++) {
                String[] tokens = line.trim().split(" ");
                if (tokens.length >= 3) {
                    break;
                }
                line = reader.readLine();
            }

            if (line == null) {
                throw new IOException("Empty data source.");
            }

            SparseDataset sparse = new SparseDataset(name);
            do {
                String[] tokens = line.trim().split(" ");
                if (tokens.length != 3) {
                    throw new ParseException("Invalid number of tokens.", nrow);
                }

                int d = Integer.parseInt(tokens[0]) - arrayIndexOrigin;
                int w = Integer.parseInt(tokens[1]) - arrayIndexOrigin;
                double c = Double.parseDouble(tokens[2]);
                sparse.set(d, w, c);

                line = reader.readLine();
                nrow++;
            } while (line != null);

            return sparse;
        } finally {
            reader.close();
        }
    }
}
