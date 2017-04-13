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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import smile.data.BinarySparseDataset;

/**
 * Parser for binary spare dataset. In the file, each line is a datum as an
 * integer list, which are the indices of nonzero elements. During the parsing,
 * the duplicated indices will be removed and indices will be sorted into
 * ascending order.
 * 
 * @author Haifeng Li
 */
public class BinarySparseDatasetParser {
    /**
     * Constructor.
     */
    public BinarySparseDatasetParser() {
    }

    /**
     * Parse a binary sparse dataset from given URI.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a binary sparse dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a binary sparse dataset from given file.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a binary sparse dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a binary sparse dataset from given file.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(File file) throws IOException, ParseException {
        String name = file.getPath();
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a binary sparse dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a binary sparse dataset from an input stream.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(InputStream stream) throws IOException, ParseException {
        return parse("Binary Sparse Dataset", stream);
    }
    
    /**
     * Parse a binary sparse dataset from an input stream.
     * @param name the name of dataset.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public BinarySparseDataset parse(String name, InputStream stream) throws IOException, ParseException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {

           BinarySparseDataset sparse = new BinarySparseDataset(name);
        
           String line = reader.readLine();
           if (line == null) {
                throw new IOException("Empty data source.");
           }
        
           Set<Integer> items = new HashSet<>();
           do {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] s = line.split("\\s+");
            
                items.clear();
                for (int i = 0; i < s.length; i++) {
                    items.add(Integer.parseInt(s[i]));
                }

                int j = 0;
                int[] point = new int[items.size()];
                for (int i : items) {
                    point[j++] = i;
                }

                Arrays.sort(point);
                sparse.add(point);
                line = reader.readLine();
            } while (line != null);

            return sparse;
        }
    }
}
