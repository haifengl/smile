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
package smile.data.parser.microarray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;

import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.NumericAttribute;

/**
 * TXT gene expression file parser. The TXT format is a tab delimited file
 * format that describes an expression dataset. It is organized as follows:
 * <p>
 * The first line contains the labels Name and Description followed by the
 * identifiers for each sample in the dataset. The Description is optional.
 * <p><code>
 * Line format: Name(tab)Description(tab)(sample 1 name)(tab)(sample 2 name) (tab) ... (sample N name)
 * </code></p>
 * <p><code>
 * Example: Name Description DLBC1_1 DLBC2_1 ... DLBC58_0
 * </code></p>
 * The remainder of the file contains data for each of the genes. There is one
 * line for each gene. Each line contains the gene name, gene description, and
 * a value for each sample in the dataset. If the first line contains the
 * Description label, include a description for each gene. If the first line
 * does not contain the Description label, do not include descriptions for
 * any gene. Gene names and descriptions can contain spaces since fields are
 * separated by tabs.
 * <p><code>
 * Line format: (gene name) (tab) (gene description) (tab) (col 1 data) (tab) (col 2 data) (tab) ... (col N data)
 * </code></p>
 * <p><code>
 * Example: AFFX-BioB-5_at AFFX-BioB-5_at (endogenous control) -104 -152 -158 ... -44
 * </code></p>
 * 
 * @author Haifeng Li
 */
public class TXTParser {
    /**
     * Constructor.
     */
    public TXTParser() {
    }

    /**
     * Parse a TXT dataset from given URI.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a TXT dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a TXT dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a TXT dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a TXT dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(File file) throws IOException, ParseException {
        return parse(file.getPath(), new FileInputStream(file));
    }

    /**
     * Parse a TXT dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a TXT dataset from an input stream.
     * @param name the name of dataset.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, InputStream stream) throws IOException, ParseException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Empty data source.");
        }

        String[] tokens = line.split("\t", -1);
        int start = 1;
        int p = tokens.length - 1;
        if (tokens[1].equalsIgnoreCase("description")) {
            start = 2;
            p = tokens.length - 2;
        }

        Attribute[] attributes = new Attribute[p];
        for (int i = 0; i < p; i++) {
            attributes[i] = new NumericAttribute(tokens[i+start]);
        }
        
        AttributeDataset data = new AttributeDataset(name, attributes);
        
        for (int i = 2; (line = reader.readLine()) != null; i++) {
            tokens = line.split("\t", -1);
            if (tokens.length != p+start) {
                throw new IOException(String.format("Invalid number of elements of line %d: %d", i, tokens.length));
            }

            double[] x = new double[p];
            for (int j = 0; j < p; j++) {
                if (tokens[j+start].isEmpty()) {
                    x[j] = Double.NaN;
                } else {
                    x[j] = Double.valueOf(tokens[j+start]);
                }
            }

            AttributeDataset.Row datum = data.add(x);
            datum.name = tokens[0];
            if (start == 2) {
                datum.description = tokens[1];
            }
        }
        
        reader.close();
        return data;
    }                
}
