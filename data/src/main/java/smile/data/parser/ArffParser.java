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
import java.io.Reader;
import java.io.StreamTokenizer;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.DateAttribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.data.StringAttribute;


/**
 * Weka ARFF (attribute relation file format) file parser. ARFF is an ASCII
 * text file format that is essentially a CSV file with a header that describes
 * the meta-data. ARFF was developed for use in the Weka machine learning
 * software.
 * <p>
 * A dataset is firstly described, beginning with the name of the dataset
 * (or the relation in ARFF terminology). Each of the variables (or attribute
 * in ARFF terminology) used to describe the observations is then identified,
 * together with their data type, each definition on a single line.
 * The actual observations are then listed, each on a single line, with fields
 * separated by commas, much like a CSV file.
 * <p>
 * Missing values in an ARFF dataset are identified using the question mark '?'.
 * <p>
 * Comments can be included in the file, introduced at the beginning of a line
 * with a '%', whereby the remainder of the line is ignored.
 * <p>
 * A significant advantage of the ARFF data file over the CSV data file is
 * the meta data information.
 * <p>
 * Also, the ability to include comments ensure we can record extra information
 * about the data set, including how it was derived, where it came from, and
 * how it might be cited.
 *
 * @author Haifeng Li
 */
public class ArffParser {

    /** The keyword used to denote the start of an arff header */
    private static final String ARFF_RELATION = "@relation";
    /** The keyword used to denote the start of the arff data section */
    private static final String ARFF_DATA = "@data";
    /** The keyword used to denote the start of an arff attribute declaration */
    private static final String ARFF_ATTRIBUTE = "@attribute";
    /** A keyword used to denote a numeric attribute */
    private static final String ARFF_ATTRIBUTE_INTEGER = "integer";
    /** A keyword used to denote a numeric attribute */
    private static final String ARFF_ATTRIBUTE_REAL = "real";
    /** A keyword used to denote a numeric attribute */
    private static final String ARFF_ATTRIBUTE_NUMERIC = "numeric";
    /** The keyword used to denote a string attribute */
    private static final String ARFF_ATTRIBUTE_STRING = "string";
    /** The keyword used to denote a date attribute */
    private static final String ARFF_ATTRIBUTE_DATE = "date";
    /** The keyword used to denote a relation-valued attribute */
    private static final String ARFF_ATTRIBUTE_RELATIONAL = "relational";
    /** The keyword used to denote the end of the declaration of a subrelation */
    private static final String ARFF_END_SUBRELATION = "@end";
    private static final String PREMATURE_END_OF_FILE = "premature end of file";
    /**
     * The column index of dependent/response variable.
     */
    private int responseIndex = -1;

    /**
     * Constructor.
     */
    public ArffParser() {
    }

    /**
     * Returns the column index (starting at 0) of dependent/response variable.
     */
    public int getResponseIndex() {
        return responseIndex;
    }

    /**
     * Sets the column index (starting at 0) of dependent/response variable.
     */
    public ArffParser setResponseIndex(int index) {
        this.responseIndex = index;
        return this;
    }
    
    /**
     * Initializes the StreamTokenizer used for reading the ARFF file.
     */
    private void initTokenizer(StreamTokenizer tokenizer) {
        tokenizer.resetSyntax();
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.wordChars(' ' + 1, '\u00FF');
        tokenizer.whitespaceChars(',', ',');
        tokenizer.commentChar('%');
        tokenizer.quoteChar('"');
        tokenizer.quoteChar('\'');
        tokenizer.ordinaryChar('{');
        tokenizer.ordinaryChar('}');
        tokenizer.eolIsSignificant(true);
    }

    /**
     * Gets next token, skipping empty lines.
     *
     * @throws IOException if reading the next token fails
     */
    private void getFirstToken(StreamTokenizer tokenizer) throws IOException {
        while (tokenizer.nextToken() == StreamTokenizer.TT_EOL) {
        }

        if ((tokenizer.ttype == '\'') || (tokenizer.ttype == '"')) {
            tokenizer.ttype = StreamTokenizer.TT_WORD;
        } else if ((tokenizer.ttype == StreamTokenizer.TT_WORD) && (tokenizer.sval.equals("?"))) {
            tokenizer.ttype = '?';
        }
    }

    /**
     * Gets token and checks if it's end of line.
     *
     * @param endOfFileOk true if EOF is OK
     * @throws IllegalStateException if it doesn't find an end of line
     */
    private void getLastToken(StreamTokenizer tokenizer, boolean endOfFileOk) throws IOException, ParseException {
        if ((tokenizer.nextToken() != StreamTokenizer.TT_EOL) && ((tokenizer.ttype != StreamTokenizer.TT_EOF) || !endOfFileOk)) {
            throw new ParseException("end of line expected", tokenizer.lineno());
        }
    }

    /**
     * Gets next token, checking for a premature and of line.
     *
     * @throws IllegalStateException if it finds a premature end of line
     */
    private void getNextToken(StreamTokenizer tokenizer) throws IOException, ParseException {
        if (tokenizer.nextToken() == StreamTokenizer.TT_EOL) {
            throw new ParseException("premature end of line", tokenizer.lineno());
        }
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        } else if ((tokenizer.ttype == '\'') || (tokenizer.ttype == '"')) {
            tokenizer.ttype = StreamTokenizer.TT_WORD;
        } else if ((tokenizer.ttype == StreamTokenizer.TT_WORD) && (tokenizer.sval.equals("?"))) {
            tokenizer.ttype = '?';
        }
    }

    /**
     * Reads and stores header of an ARFF file.
     *
     * @param attributes the set of attributes in this relation.
     * @return the name of relation.
     * @throws IllegalStateException if the information is not read successfully
     */
    private String readHeader(StreamTokenizer tokenizer, List<Attribute> attributes) throws IOException, ParseException {
        /// The name of dataset.
        String relationName = null;
        // clear attribute set, which may be from previous parsing of other datasets.
        attributes.clear();

        // Get name of relation.
        getFirstToken(tokenizer);
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }
        if (ARFF_RELATION.equalsIgnoreCase(tokenizer.sval)) {
            getNextToken(tokenizer);
            relationName = tokenizer.sval;
            getLastToken(tokenizer, false);
        } else {
            throw new ParseException("keyword " + ARFF_RELATION + " expected", tokenizer.lineno());
        }

        // Get attribute declarations.
        getFirstToken(tokenizer);
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }

        while (ARFF_ATTRIBUTE.equalsIgnoreCase(tokenizer.sval)) {
            attributes.add(parseAttribute(tokenizer));
        }

        // Check if data part follows. We can't easily check for EOL.
        if (!ARFF_DATA.equalsIgnoreCase(tokenizer.sval)) {
            throw new ParseException("keyword " + ARFF_DATA + " expected", tokenizer.lineno());
        }

        // Check if any attributes have been declared.
        if (attributes.isEmpty()) {
            throw new ParseException("no attributes declared", tokenizer.lineno());
        }
        
        if (responseIndex >= attributes.size()) {
            throw new ParseException("Invalid response variable index", responseIndex);            
        }
        
        return relationName;
    }

    /**
     * Parses the attribute declaration.
     *
     * @return an attributes in this relation
     * @throws IOException 	if the information is not read
     * 				successfully
     */
    private Attribute parseAttribute(StreamTokenizer tokenizer) throws IOException, ParseException {
        Attribute attribute = null;

        // Get attribute name.
        getNextToken(tokenizer);
        String attributeName = tokenizer.sval;
        getNextToken(tokenizer);

        // Check if attribute is nominal.
        if (tokenizer.ttype == StreamTokenizer.TT_WORD) {

            // Attribute is real, integer, or string.
            if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_REAL) ||
                    tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_INTEGER) ||
                    tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_NUMERIC)) {
                attribute = new NumericAttribute(attributeName);
                readTillEOL(tokenizer);

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_STRING)) {
                attribute = new StringAttribute(attributeName);
                readTillEOL(tokenizer);

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_DATE)) {
                String format = null;
                if (tokenizer.nextToken() != StreamTokenizer.TT_EOL) {
                    if ((tokenizer.ttype != StreamTokenizer.TT_WORD) && (tokenizer.ttype != '\'') && (tokenizer.ttype != '\"')) {
                        throw new ParseException("not a valid date format", tokenizer.lineno());
                    }
                    format = tokenizer.sval;
                    readTillEOL(tokenizer);
                } else {
                    tokenizer.pushBack();
                }
                attribute = new DateAttribute(attributeName, null, format);
                readTillEOL(tokenizer);

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_RELATIONAL)) {
                readTillEOL(tokenizer);

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_END_SUBRELATION)) {
                getNextToken(tokenizer);

            } else {
                throw new ParseException("Invalid attribute type or invalid enumeration", tokenizer.lineno());
            }
        } else {

            // Attribute is nominal.
            List<String> attributeValues = new ArrayList<>();
            tokenizer.pushBack();

            // Get values for nominal attribute.
            if (tokenizer.nextToken() != '{') {
                throw new ParseException("{ expected at beginning of enumeration", tokenizer.lineno());
            }
            while (tokenizer.nextToken() != '}') {
                if (tokenizer.ttype == StreamTokenizer.TT_EOL) {
                    throw new ParseException("} expected at end of enumeration", tokenizer.lineno());
                } else {
                    attributeValues.add(tokenizer.sval.trim());
                }
            }

            String[] values = new String[attributeValues.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = attributeValues.get(i);
            }
            attribute = new NominalAttribute(attributeName, values);
        }

        getLastToken(tokenizer, false);
        getFirstToken(tokenizer);
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }

        return attribute;
    }

    /**
     * Reads and skips all tokens before next end of line token.
     *
     * @throws IOException in case something goes wrong
     */
    private void readTillEOL(StreamTokenizer tokenizer) throws IOException {
        while (tokenizer.nextToken() != StreamTokenizer.TT_EOL) {
        }

        tokenizer.pushBack();
    }

    /**
     * Returns the attribute set of given URI.
     * @throws java.io.IOException
     */
    public static Attribute[] getAttributes(URI uri) throws IOException, ParseException {
        return getAttributes(new File(uri));
    }

    /**
     * Returns the attribute set of given file.
     * @throws java.io.IOException
     */
    public static Attribute[] getAttributes(String path) throws IOException, ParseException {
        return getAttributes(new File(path));
    }

    /**
     * Returns the attribute set of given file.
     * @throws java.io.IOException
     */
    public static Attribute[] getAttributes(File file) throws IOException, ParseException {
        return getAttributes(new FileInputStream(file));
    }

    /**
     * Returns the attribute set of given stream.
     */
    public static Attribute[] getAttributes(InputStream stream) throws IOException, ParseException {
        Reader r = new BufferedReader(new InputStreamReader(stream));
        StreamTokenizer tokenizer = new StreamTokenizer(r);
        
        ArffParser parser = new ArffParser();
        parser.initTokenizer(tokenizer);

        List<Attribute> attributes = new ArrayList<>();
        parser.readHeader(tokenizer, attributes);

        return attributes.toArray(new Attribute[attributes.size()]);
    }
    
    /**
     * Parse a dataset from given URI.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a dataset from given file.
     * @throws java.io.IOException
     */
    public AttributeDataset parse(File file) throws IOException, ParseException {
        return parse(new FileInputStream(file));
    }

    /**
     * Parse a dataset from given stream.
     */
    public AttributeDataset parse(InputStream stream) throws IOException, ParseException {
        try (Reader r = new BufferedReader(new InputStreamReader(stream))) {
            StreamTokenizer tokenizer = new StreamTokenizer(r);
            initTokenizer(tokenizer);

            List<Attribute> attributes = new ArrayList<>();
            String relationName = readHeader(tokenizer, attributes);

            if (attributes.isEmpty()) {
                throw new IOException("no header information available");
            }
        
            Attribute response = null;
            Attribute[] attr = new Attribute[attributes.size()];
            attributes.toArray(attr);
        
            for (int i = 0; i < attributes.size(); i++) {
                if (responseIndex == i) {
                    response = attributes.remove(i);
                    break;
                }
            }
        
            AttributeDataset data = new AttributeDataset(relationName, attributes.toArray(new Attribute[attributes.size()]), response);
        
            while (true) {
                // Check if end of file reached.
                getFirstToken(tokenizer);
                if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
                    break;
                }

                // Parse instance
                if (tokenizer.ttype == '{') {
                    readSparseInstance(tokenizer, data, attr);
                } else {
                    readInstance(tokenizer, data, attr);
                }
            }
        
            for (Attribute attribute : attributes) {
                if (attribute instanceof NominalAttribute) {
                    NominalAttribute a = (NominalAttribute) attribute;
                    a.setOpen(false);
                }
            
                if (attribute instanceof StringAttribute) {
                    StringAttribute a = (StringAttribute) attribute;
                    a.setOpen(false);
                }
            }
        
            return data;
        }
    }

    /**
     * Reads a single instance.
     * @throws ParseException if the information is not read successfully
     */
    private void readInstance(StreamTokenizer tokenizer, AttributeDataset data, Attribute[] attributes) throws IOException, ParseException {
        double[] x = responseIndex >= 0 ? new double[attributes.length - 1] : new double[attributes.length];
        double y = Double.NaN;
        
        // Get values for all attributes.
        for (int i = 0, k = 0; i < attributes.length; i++) {
            // Get next token
            if (i > 0) {
                getNextToken(tokenizer);
            }

            if (i == responseIndex) {
                if (tokenizer.ttype == '?') {
                    y = Double.NaN;
                } else {
                    y = attributes[i].valueOf(tokenizer.sval);
                }
            } else {
                if (tokenizer.ttype == '?') {
                    x[k++] = Double.NaN;
                } else {
                    x[k++] = attributes[i].valueOf(tokenizer.sval);
                }
            }
        }

        if (Double.isNaN(y)) data.add(x); else data.add(x, y);
    }

    /**
     * Reads a sparse instance using the tokenizer.
     * @throws ParseException if the information is not read successfully
     */
    private void readSparseInstance(StreamTokenizer tokenizer, AttributeDataset data, Attribute[] attributes) throws IOException, ParseException {
        double[] x = responseIndex >= 0 ? new double[attributes.length - 1] : new double[attributes.length];
        double y = Double.NaN;
        int index = -1;
        
        // Get values for all attributes.
        do {
            getNextToken(tokenizer);
            
            // end of instance
            if (tokenizer.ttype == '}') {
                break;
            }
            
            String s = tokenizer.sval.trim();
            if (index < 0) {
                index = Integer.parseInt(s);
                if (index < 0 || index >= attributes.length) {
                    throw new ParseException("Invalid attribute index: " + index, tokenizer.lineno());
                }
                
            } else {
                
                String val = s;
                if (index != responseIndex) {
                    if (val.equals("?")) {
                        x[index] = Double.NaN;
                    } else {
                        x[index] = attributes[index].valueOf(val);
                    }
                } else {
                    if (val.equals("?")) {
                        y = Double.NaN;
                    } else {
                        y = attributes[index].valueOf(val);
                    }
                }
                
                index = -1;
            }
            
        } while (tokenizer.ttype == StreamTokenizer.TT_WORD);
        
        if (Double.isNaN(y)) data.add(x); else data.add(x, y);
    }
}
