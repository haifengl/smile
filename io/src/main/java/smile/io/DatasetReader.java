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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.Dataset;
import smile.data.Instance;
import smile.data.type.StructType;
import smile.math.SparseArray;

/**
 * Interface to load a Dataset from external storage systems.
 * 
 * @author Haifeng Li
 */
public class DatasetReader {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DatasetReader.class);

    /** Reads a limited number of records. */
    private int limit = Integer.MAX_VALUE;
    /** CSV format. */
    private CSVFormat format = CSVFormat.DEFAULT;
    /** Avro schema. */
    private Schema schema;
    /** CSV or JSON schema. */
    private StructType struct;
    /** JSON mode. */
    private JSON.Mode mode = JSON.Mode.SINGLE_LINE;

    /**
     * Constructor.
     */
    public DatasetReader() {

    }

    /**
     * Reads a limited number of records.
     */
    public void limit(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("Invalid limit: " + max);
        }
        this.limit = max;
    }

    /**
     * Sets the CSV format.
     */
    public void format(CSVFormat format) {
        this.format = format;
    }

    /**
     * Sets the JSON read mode.
     */
    public void mode(JSON.Mode mode) {
        this.mode = mode;
    }

    /**
     * Sets the Avro schema.
     */
    public void schema(Schema schema) {
        this.schema = schema;
    }

    /**
     * Sets the CSV or JSON schema.
     */
    public void schema(StructType schema) {
        this.struct = schema;
    }

    /** Reads a CSV file. */
    public DataFrame csv(Path path) throws IOException {
        CSV csv = new CSV(format);
        if (struct != null) csv.schema(struct);
        return csv.read(path, limit);
    }

    /** Reads a JSON file. */
    public DataFrame json(Path path) throws IOException {
        JSON json = new JSON().mode(mode);
        if (struct != null) json.schema(struct);
        return json.read(path, limit);
    }

    /**
     * Reads an ARFF file. Weka ARFF (attribute relation file format) is an ASCII
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
     * @param path the input file path.
     */
    public DataFrame arff(Path path) throws IOException, ParseException {
        Arff arff = new Arff(path);
        return arff.read(limit);
    }

    /**
     * Reads a SAS7BDAT file.
     *
     * @param path the input file path.
     */
    public DataFrame sas(Path path) throws IOException {
        SAS sas = new SAS();
        return sas.read(path, limit);
    }

    /**
     * Reads an Apache Arrow file.
     * Apache Arrow is a cross-language development platform for in-memory data.
     * It specifies a standardized language-independent columnar memory format
     * for flat and hierarchical data, organized for efficient analytic
     * operations on modern hardware.
     *
     * @param path the input file path.
     */
    public DataFrame arrow(Path path) throws IOException {
        Arrow arrow = new Arrow();
        return arrow.read(path, limit);
    }

    /**
     * Reads an Apache Avro file.
     *
     * @param path the input file path.
     */
    public DataFrame avro(Path path) throws IOException {
        if (schema == null) {
            throw new IllegalStateException("Avro schema is not set yet. Call schema(org.apache.avro.Schema) first.");
        }

        Avro avro = new Avro(schema);
        return avro.read(path, limit);
    }

    /**
     * Reads an Apache Parquet file.
     *
     * @param path the input file path.
     */
    public DataFrame parquet(Path path) throws IOException {
        Parquet parquet = new Parquet();
        return parquet.read(path, limit);
    }

    /**
     * Reads a libsvm sparse dataset. The format of libsvm file is:
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
     * @param path the input file path.
     */
    public Dataset<Instance<SparseArray>> libsvm(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Empty data source.");
            }

            // detect if the response variable is read or integer label.
            String token = line.trim().split("\\s+")[0];
            boolean classification = true;
            try {
                Integer.valueOf(token);
            } catch (NumberFormatException e) {
                try {
                    Double.valueOf(token);
                    classification = false;
                } catch (NumberFormatException ex) {
                    logger.error("Failed to parse {}", token, ex);
                    throw new NumberFormatException("Unrecognized response variable value: " + token);
                }
            }

            List<Instance<SparseArray>> data = new ArrayList<>();
            do {
                String[] tokens = line.trim().split("\\s+");
                String firstToken = tokens[0];

                SparseArray row = new SparseArray();
                for (int k = 1; k < tokens.length; k++) {
                    String[] pair = tokens[k].split(":");
                    if (pair.length != 2) {
                        throw new NumberFormatException("Invalid token: " + tokens[k]);
                    }

                    int j = Integer.parseInt(pair[0]) - 1;
                    double x = Double.parseDouble(pair[1]);
                    row.set(j, x);
                }

                if (classification) {
                    data.add(new Instance<SparseArray>() {
                        int y = Integer.parseInt(firstToken);
                        @Override
                        public SparseArray x() {
                            return row;
                        }

                        @Override
                        public int label() {
                            return y;
                        }
                    });
                } else {
                    data.add(new Instance<SparseArray>() {
                        double y = Double.parseDouble(firstToken);
                        @Override
                        public SparseArray x() {
                            return row;
                        }

                        @Override
                        public double y() {
                            return y;
                        }
                    });
                }

                line = reader.readLine();
            } while (line != null);

            return Dataset.of(data);
        }
    }    
}
