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
 * Gene Cluster Text file parser. The GCT format is a tab delimited
 * file format that describes a gene expression dataset. It is organized as
 * follows: 
 * <p>
 * The first line contains the version string and is always the same for this
 * file format. Therefore, the first line must be as follows:
 * <p><code>
 * #1.2
 * </code></p>
 * The second line contains numbers indicating the size of the data table
 * that is contained in the remainder of the file. Note that the name and
 * description columns are not included in the number of data columns. 
 * <p>
 * Line format:
 * <p><code>
 * (# of data rows) (tab) (# of data columns)
 * </code></p>
 * Example:
 * <p><code>
 * 7129 58
 * </code></p>
 * The third line contains a list of identifiers for the samples associated
 * with each of the columns in the remainder of the file. 
 * <p>
 * Line format:
 * <p><code>
 * Name(tab)Description(tab)(sample 1 name)(tab)(sample 2 name) (tab) ... (sample N name)
 * </code></p>
 * Example:
 * <p><code>
 * Name Description DLBC1_1 DLBC2_1 ... DLBC58_0
 * </code></p>
 * The remainder of the data file contains data for each of the genes. There
 * is one row for each gene and one column for each of the samples. The number
 * of rows and columns should agree with the number of rows and columns
 * specified on line 2. Each row contains a name, a description, and an
 * intensity value for each sample. Names and descriptions can contain spaces,
 * but may not be empty. If no description is available, enter a text string
 * such as NA or NULL. Intensity values may be missing. To specify a missing
 * intensity value, leave the field empty: ...(tab)(tab).... 
 * <p>
 * Line format:
 * <p><code>
 * (gene name) (tab) (gene description) (tab) (col 1 data) (tab) (col 2 data) (tab) ... (col N data)
 * </code></p>
 * Example:
 * <p><code>
 * AFFX-BioB-5_at AFFX-BioB-5_at (endogenous control) -104 -152 -158 ... -44
 * </code></p>
 * 
 * @author Haifeng Li
 */
public class GCTParser {
    /**
     * Constructor.
     */
    public GCTParser() {
    }

    /**
     * Parse a GCT dataset from given URI.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a GCT dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a GCT dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a GCT dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a GCT dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(File file) throws IOException, ParseException {
        return parse(file.getPath(), new FileInputStream(file));
    }

    /**
     * Parse a GCT dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a GCT dataset from an input stream.
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

        if (!line.equals("#1.2")) {
            throw new IOException("Invalid version.");            
        }
        
        line = reader.readLine();
        if (line == null) {
            throw new IOException("Premature end of file.");
        }

        String[] tokens = line.split("\t", -1);
        if (tokens.length != 2) {
            throw new IOException("Invalid data size inforamation.");            
        }
        
        int n = Integer.parseInt(tokens[0]);
        int p = Integer.parseInt(tokens[1]);
        if (n <= 0 || p <= 0) {
            throw new IOException(String.format("Invalid data size %d x %d.", n, p));                        
        }
        
        Attribute[] attributes = new Attribute[p];
        line = reader.readLine();
        if (line == null) {
            throw new IOException("Premature end of file.");
        }

        tokens = line.split("\t", -1);
        if (tokens.length != p + 2) {
            throw new IOException("Invalid title header.");            
        }
        
        for (int i = 0; i < p; i++) {
            attributes[i] = new NumericAttribute(tokens[i+2]);
        }
        
        AttributeDataset data = new AttributeDataset(name, attributes);
        
        for (int i = 0; i < n; i++) {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("Premature end of file.");
            }
            
            tokens = line.split("\t", -1);
            if (tokens.length != p + 2) {
                throw new IOException(String.format("Invalid number of elements of line %d: %d", i+4, tokens.length));
            }

            double[] x = new double[p];
            for (int j = 0; j < p; j++) {
                if (tokens[j+2].isEmpty()) {
                    x[j] = Double.NaN;
                } else {
                    x[j] = Double.valueOf(tokens[j+2]);
                }
            }

            AttributeDataset.Row datum = data.add(x);
            datum.name = tokens[0];
            datum.description = tokens[1];
        }
        
        reader.close();
        return data;
    }    
}
