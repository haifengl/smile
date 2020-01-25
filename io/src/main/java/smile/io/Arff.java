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
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    /** The lambda to parse fields. */
    private List<Function<String, Object>> parser;
    /** Attribute name path in case of sub-relations. */
    private String path = "";

    /**
     * Constructor.
     */
    public Arff(String path) throws IOException, ParseException, URISyntaxException {
        this(Input.reader(path));
    }

    /**
     * Constructor.
     */
    public Arff(String path, Charset charset) throws IOException, ParseException, URISyntaxException {
        this(Input.reader(path, charset));
    }

    /**
     * Constructor.
     */
    public Arff(Path path) throws IOException, ParseException {
        this(Files.newBufferedReader(path));
    }

    /**
     * Constructor.
     */
    public Arff(Path path, Charset charset) throws IOException, ParseException {
        this(Files.newBufferedReader(path, charset));
    }

    /**
     * Constructor.
     */
    public Arff(Reader reader) throws IOException, ParseException {
        this.reader = reader;

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
        parser = schema.parser();
    }

    /**
     * Reads the attribute declaration.
     *
     * @return an attributes in this relation
     * @throws IOException if the information is not read successfully
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

            NominalScale scale = new NominalScale(attributeValues);
            attribute = new StructField(name, scale.type(), scale);
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

        schema = schema.boxed(rows);
        return DataFrame.of(rows, schema);
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
                x[i] = parser.get(i).apply(tokenizer.sval);
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
                x[i] = parser.get(i).apply(val);
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

    /**
     * Writes the data frame to an ARFF file.
     * @param df the data frame.
     * @param path the file path.
     * @param relation the relation name of ARFF.
     */
    public static void write(DataFrame df, Path path, String relation) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newOutputStream(path))) {

            writer.print("@RELATION ");
            writer.println(relation);

            for (StructField field : df.schema().fields()) {
                writeField(writer, field);
            }

            writer.println("@DATA");

            int p = df.ncols();
            df.stream().forEach(t -> {
                String line = IntStream.range(0, p).mapToObj(i -> t.toString()).collect(Collectors.joining(","));
                writer.println(line);
            });
        }
    }

    /** Write the meta of field to ARFF file. */
    private static void writeField(PrintWriter writer, StructField field) throws IOException {
        writer.print("@ATTRIBUTE ");
        writer.print(field.name);
        if (field.type.isFloating()) writer.println(" REAL");
        else if (field.type.isString()) writer.println(" STRING");
        else if (field.type.id() == DataType.ID.DateTime) writer.println(" DATE \"yyyy-MM-dd HH:mm:ss\"");
        else if (field.type.isIntegral()) {
            if (field.measure instanceof NominalScale) {
                NominalScale scale = (NominalScale) field.measure;
                String levels = Arrays.stream(scale.levels()).collect(Collectors.joining(",", " {", "}"));
                writer.println(levels);
            } else {
                writer.println(" REAL");
            }
        }
    }
}
