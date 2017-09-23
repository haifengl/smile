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
 * Stanford cDNA file parser. The PCL file format is a tab delimited
 * file format that describes a gene expression dataset. The first three
 * columns are as follows:
 * <dl>
 * <dt>Name</dt>
 * <dd>This column will contain a name ascribed to the entity on that row, such
 * as ORF name, or CLONEID. The column itself can be named anything, but by
 * convention is named YORF when it contains yeast ORF names, CLID when it
 * contains clone IDs, and LUID when it contains LUIDs. This column MUST contain
 * some text on every row.</dd>
 * <dt>Description</dt>
 * <dd>This column can contain descriptive information about the entity, e.g.
 * process or function, or gene symbol. It too can be named anything. It can
 * optionally be left blank, but the column itself must be present.</dd>
 * <dt>GWEIGHT</dt>
 * <dd>This column allows you to weight genes with different weights, for
 * instance if a gene appears on an array twice, you may want to give them
 * a weight of 0.5 each. For the most part people leave this column with a
 * value of 1 for every gene. This column must be present, and each row must
 * have an entry.</dd>
 * </dl>
 * In addition the file must begin with the following two rows:
 * <dl>
 * <dt>Row 1</dt>
 * <dd>This contains the column headers as described above for columns 1, 2
 * and 3, then contains the experiment names for all the data columns that
 * exist in the file. Each data column must have a text entry as a name for
 * that column.</dd>
 * <dt>Row 2</dt>
 * <dd>This is the EWEIGHT row. The entry in the first column for this row
 * should say EWEIGHT, then for each experiment, there should be an EWEIGHT
 * value. This will usually be 1, but if the same experiment is duplicated
 * twice, you may want to give these repeats an EWEIGHT of 0.5.</dd>
 * </dl>
 * The remaining cells in the file contain the actual data, such that the row
 * and column specifies to which gene and which experiment a particular piece
 * of data corresponds.
 * <p>
 * In general the PCL file will contain log-transformed data, which is needed
 * for clustering to work properly.
 * 
 * @author Haifeng Li
 */
public class PCLParser {
    /**
     * Constructor.
     */
    public PCLParser() {
    }

    /**
     * Parse a PCL dataset from given URI.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a PCL dataset from given URI.
     * @param uri the URI of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, URI uri) throws IOException, ParseException {
        return parse(name, new File(uri));
    }

    /**
     * Parse a PCL dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a PCL dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, String path) throws IOException, ParseException {
        return parse(name, new File(path));
    }

    /**
     * Parse a PCL dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(File file) throws IOException, ParseException {
        return parse(file.getPath(), new FileInputStream(file));
    }

    /**
     * Parse a PCL dataset from given file.
     * @param file the file of data source.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String name, File file) throws IOException, ParseException {
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a PCL dataset from an input stream.
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
        int p = tokens.length - 3;

        line = reader.readLine();
        if (line == null) {
            throw new IOException("Premature end of file.");
        }

        String[] weight = line.split("\t", -1);
        if (weight.length != tokens.length) {
            throw new IOException("Invalid sample weight header.");
        }
        
        Attribute[] attributes = new Attribute[p];
        for (int i = 0; i < p; i++) {
            attributes[i] = new NumericAttribute(tokens[i+3], null, Double.valueOf(weight[i+3]));
        }
        
        AttributeDataset data = new AttributeDataset(name, attributes);
        
        for (int i = 3; (line = reader.readLine()) != null; i++) {
            tokens = line.split("\t", -1);
            if (tokens.length != weight.length) {
                throw new IOException(String.format("Invalid number of elements of line %d: %d", i, tokens.length));
            }

            double[] x = new double[p];
            for (int j = 0; j < p; j++) {
                if (tokens[j+3].isEmpty()) {
                    x[j] = Double.NaN;
                } else {
                    x[j] = Double.valueOf(tokens[j+3]);
                }
            }

            AttributeDataset.Row datum = data.add(x);
            datum.name = tokens[0];
            datum.description = tokens[1];
            datum.weight = Double.valueOf(tokens[2]);
        }
        
        reader.close();
        return data;
    }            
}
