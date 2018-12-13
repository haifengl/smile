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
package smile.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.NominalScale;
import smile.data.type.*;

/**
 * Weka ARFF (attribute relation file format) is an ASCII
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
public class Arff implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Arff.class);

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

    /** Buffered file reader. */
    private Reader reader;
    /** The tokenizer to parse the file. */
    private StreamTokenizer tokenizer;
    /** The name of ARFF relation. */
    private String name;
    /** The schema of ARFF relation. */
    private StructType schema;
    /** The map of field name to its scale of measure. */
    private Map<String, NominalScale> measure;
    /** The lambda to parse fields. */
    private Parser[] parser;
    /** Attribute name path in case of sub-relations. */
    private String path = "";

    /** Parser lambda interface that allows throw a checked exception. */
    interface Parser {
        Object apply(String s) throws ParseException;
    }

    /**
     * Constructor.
     */
    public Arff(Path path) throws IOException, ParseException {
        reader = Files.newBufferedReader(path);

        tokenizer = new StreamTokenizer(reader);
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

        measure = new HashMap<>();
        readHeader();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /** Returns the name of relation. */
    public String name() {
        return name;
    }

    /**
     * Returns the attribute set of given stream.
     */
    public StructType schema() {
        return schema;
    }

    /**
     * Gets next token, skipping empty lines.
     *
     * @throws IOException if reading the next token fails
     */
    private void getFirstToken() throws IOException {
        while (tokenizer.nextToken() == StreamTokenizer.TT_EOL) {
            // empty lines
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
    private void getLastToken(boolean endOfFileOk) throws IOException, ParseException {
        if ((tokenizer.nextToken() != StreamTokenizer.TT_EOL) && ((tokenizer.ttype != StreamTokenizer.TT_EOF) || !endOfFileOk)) {
            throw new ParseException("end of line expected", tokenizer.lineno());
        }
    }

    /**
     * Gets next token, checking for a premature and of line.
     *
     * @throws IllegalStateException if it finds a premature end of line
     */
    private void getNextToken() throws IOException, ParseException {
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
     * @return the schema of relation.
     * @throws IllegalStateException if the information is not read successfully
     */
    private void readHeader() throws IOException, ParseException {
        List<StructField> fields = new ArrayList<>();

        // Get name of relation.
        getFirstToken();
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }
        if (ARFF_RELATION.equalsIgnoreCase(tokenizer.sval)) {
            getNextToken();
            name = tokenizer.sval;
            logger.info("Read ARFF relation {}", name);
            getLastToken(false);
        } else {
            throw new ParseException("keyword " + ARFF_RELATION + " expected", tokenizer.lineno());
        }

        // Get attribute declarations.
        getFirstToken();
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }

        while (ARFF_ATTRIBUTE.equalsIgnoreCase(tokenizer.sval)) {
            StructField attribute = nextAttribute();
            // We may meet an relational attribute, which parseAttribute returns null
            // as it flats the relational attribute out.
            if (attribute != null) {
                fields.add(attribute);
            }
        }

        // Check if data part follows. We can't easily check for EOL.
        if (!ARFF_DATA.equalsIgnoreCase(tokenizer.sval)) {
            throw new ParseException("keyword " + ARFF_DATA + " expected", tokenizer.lineno());
        }

        // Check if any attributes have been declared.
        if (fields.isEmpty()) {
            throw new ParseException("no attributes declared", tokenizer.lineno());
        }
        
        schema = DataTypes.struct(fields);
        schema.measure().putAll(measure);
        parser = new Parser[fields.size()];
        for (int i = 0; i < parser.length; i++) {
            final StructField field = fields.get(i);
            NominalScale scale = measure.get(field.name);
            if (scale == null) {
                parser[i] = s -> field.type.valueOf(s);
            } else {
                parser[i] = s -> scale.valueOf(s);
            }
        }
    }

    /**
     * Reads the attribute declaration.
     *
     * @return an attributes in this relation
     * @throws IOException 	if the information is not read successfully
     */
    private StructField nextAttribute() throws IOException, ParseException {
        StructField attribute = null;

        // Get attribute name.
        getNextToken();
        String name = attributeName(tokenizer.sval);
        getNextToken();

        // Check if attribute is nominal.
        if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
            // Attribute is real, integer, or string.
            if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_NUMERIC)) {
                attribute = new StructField(name, DataTypes.DoubleType);
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_REAL)) {
                attribute = new StructField(name, DataTypes.FloatType);
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_INTEGER)) {
                attribute = new StructField(name, DataTypes.IntegerType);
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_STRING)) {
                attribute = new StructField(name, DataTypes.StringType);
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_DATE)) {
                if (tokenizer.nextToken() != StreamTokenizer.TT_EOL) {
                    if ((tokenizer.ttype != StreamTokenizer.TT_WORD) && (tokenizer.ttype != '\'') && (tokenizer.ttype != '\"')) {
                        throw new ParseException("not a valid date format", tokenizer.lineno());
                    }
                    attribute = new StructField(name, DataTypes.datetime(tokenizer.sval));
                    readTillEOL();
                } else {
                    attribute = new StructField(name, DataTypes.DateTimeType);
                    tokenizer.pushBack();
                }
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_ATTRIBUTE_RELATIONAL)) {
                logger.info("Encounter relational attribute {}. Flat out its attributes to top level.", name);
                path = path + "." + name;
                readTillEOL();

            } else if (tokenizer.sval.equalsIgnoreCase(ARFF_END_SUBRELATION)) {
                path = path.substring(0, path.lastIndexOf('.'));
                getNextToken();

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

            String[] levels = attributeValues.toArray(new String[attributeValues.size()]);
            if (levels.length <= Byte.MAX_VALUE + 1) {
                attribute = new StructField(name, DataTypes.ByteType);
            } else if (levels.length <= Short.MAX_VALUE + 1) {
                attribute = new StructField(name, DataTypes.ShortType);
            } else {
                attribute = new StructField(name, DataTypes.IntegerType);
            }
            measure.put(name, new NominalScale(levels));
        }

        getLastToken(false);
        getFirstToken();
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
            throw new ParseException(PREMATURE_END_OF_FILE, tokenizer.lineno());
        }

        return attribute;
    }

    /** Returns the attribute name. */
    private String attributeName(String name) {
        return path.length() == 0 ? name : path + "." + name;
    }

    /**
     * Reads and skips all tokens before next end of line token.
     *
     * @throws IOException in case something goes wrong
     */
    private void readTillEOL() throws IOException {
        while (tokenizer.nextToken() != StreamTokenizer.TT_EOL) {
            // skip all the tokens before EOL
        }

        // push back the EOL token
        tokenizer.pushBack();
    }

    /**
     * Reads all the records.
     */
    public DataFrame read() throws IOException, ParseException {
        return read(Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records.
     * @param limit reads a limited number of records.
     */
    public DataFrame read(int limit) throws IOException, ParseException {
        if (limit <= 0) {
            throw new IllegalArgumentException("Invalid limit: " + limit);
        }

        List<Tuple> rows = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            // Check if end of file reached.
            getFirstToken();
            if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
                break;
            }

            // Parse instance
            Object[] row = tokenizer.ttype == '{' ? readSparseInstance() : readInstance();
            rows.add(Tuple.of(row, schema));
        }

        schema.boxed(rows);
        return DataFrame.of(rows);
    }

    /**
     * Reads a single instance.
     * @throws ParseException if the information is not read successfully
     */
    private Object[] readInstance() throws IOException, ParseException {
        StructField[] fields = schema.fields();
        int p = fields.length;
        Object[] x = new Object[p];

        // Get values for all attributes.
        for (int i = 0; i < p; i++) {
            // Get next token
            if (i > 0) {
                getNextToken();
            }

            if (tokenizer.ttype != '?') {
                x[i] = parser[i].apply(tokenizer.sval);
            }
        }

        return x;
    }

    /**
     * Reads a sparse instance using the tokenizer.
     * @throws ParseException if the information is not read successfully
     */
    private Object[] readSparseInstance() throws IOException, ParseException {
        StructField[] fields = schema.fields();
        int p = fields.length;
        Object[] x = new Object[p];

        // Get values for all attributes.
        do {
            getNextToken();
            
            // end of instance
            if (tokenizer.ttype == '}') {
                break;
            }
            
            int i = Integer.parseInt(tokenizer.sval.trim());
            if (i < 0 || i >= p) {
                throw new ParseException("Invalid attribute index: " + i, tokenizer.lineno());
            }

            getNextToken();
            String val = tokenizer.sval.trim();
            if (!val.equals("?")) {
                x[i] = parser[i].apply(val);
            }
        } while (tokenizer.ttype == StreamTokenizer.TT_WORD);

        for (int i = 0; i < x.length; i++) {
            if (x[i] == null) {
                StructField field = schema.field(i);
                if (field.type.isByte()) {
                    x[i] = (byte) 0;
                } else if (field.type.isShort()) {
                    x[i] = (short) 0;
                } else if (field.type.isInt()) {
                    x[i] = 0;
                } else if (field.type.isFloat()) {
                    x[i] = 0.0f;
                } else {
                    x[i] = 0.0;
                }
            }
        }

        return x;
    }
}
