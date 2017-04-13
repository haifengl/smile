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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.SparseDataset;
import smile.math.Math;

/**
 * LIBSVM (and SVMLight) data parser. The format of libsvm file is:
 * <p>
 * &lt;label&gt; &lt;index1&gt;:&lt;value1&gt; &lt;index2&gt;:&lt;value2&gt; ...
 * <p>
 * where &lt;label&gt; is the target value of the training data.
 * For classification, it should be an integer which identifies a class
 * (multi-class classification is supported). For regression, it's any real
 * number. For one-class SVM, it's not used so can be any number.
 * &lt;index&gt; is an integer starting from 1, and &lt;value&gt;
 * is a real number. The indices must be in an ascending order. The labels in
 * the testing data file are only used to calculate accuracy or error. If they
 * are unknown, just fill this column with a number. 
 * 
 * @author Haifeng Li
 */
public class LibsvmParser {
    private static final Logger logger = LoggerFactory.getLogger(LibsvmParser.class);

    /**
     * Constructor.
     */
    public LibsvmParser() {
    }

    /**
     * Parse a libsvm sparse dataset from given URI.
     * @throws java.io.IOException
     */
    public SparseDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a libsvm sparse dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a libsvm sparse dataset from given file.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a libsvm sparse dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a libsvm sparse dataset from given file.
     * @throws java.io.IOException
     */
    public SparseDataset parse(File file) throws IOException, ParseException {
        String name = file.getPath();
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a libsvm sparse dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a libsvm sparse dataset from an input stream.
     * @param name the name of dataset.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public SparseDataset parse(String name, InputStream stream) throws IOException, ParseException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Empty data source.");
            }

            String[] tokens = line.trim().split("\\s+");
            boolean classification = true;
            Attribute response = null;
            try {
                Integer.valueOf(tokens[0]);
                response = new NominalAttribute("class");
            } catch (NumberFormatException e) {
                try {
                    Double.valueOf(tokens[0]);
                    response = new NominalAttribute("response");
                    classification = false;
                } catch (NumberFormatException ex) {
                    logger.error("Failed to parse {}", tokens[0], ex);
                    throw new NumberFormatException("Unrecognized response variable value: " + tokens[0]);
                }
            }

            SparseDataset sparse = new SparseDataset(name, response);
            for (int i = 0; line != null; i++) {
                tokens = line.trim().split("\\s+");

                if (classification) {
                    int y = Integer.parseInt(tokens[0]);
                    sparse.set(i, y);
                } else {
                    double y = Double.parseDouble(tokens[0]);
                    sparse.set(i, y);
                }

                for (int k = 1; k < tokens.length; k++) {
                    String[] pair = tokens[k].split(":");
                    if (pair.length != 2) {
                        throw new NumberFormatException("Invalid data: " + tokens[k]);
                    }

                    int j = Integer.parseInt(pair[0]) - 1;
                    double x = Double.parseDouble(pair[1]);
                    sparse.set(i, j, x);
                }

                line = reader.readLine();
            }

            if (classification) {
                int n = sparse.size();
                int[] y = sparse.toArray(new int[n]);
                int[] label = Math.unique(y);
                Arrays.sort(label);
                for (int c : label) {
                    response.valueOf(String.valueOf(c));
                }

                for (int i = 0; i < n; i++) {
                    sparse.get(i).y = Arrays.binarySearch(label, y[i]);
                }
            }

            return sparse;
        } finally {
            reader.close();
        }
    }    
}
