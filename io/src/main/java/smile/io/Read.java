/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.Dataset;
import smile.data.Instance;
import smile.data.type.StructType;
import smile.util.SparseArray;
import smile.util.Strings;

/**
 * Reads data from external storage systems.
 * 
 * @author Haifeng Li
 */
public interface Read {
    /**
     * Reads a data file. Infers the data format by the file name extension.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame data(String path) throws IOException, URISyntaxException, ParseException {
        return data(path, null);
    }

    /**
     * Reads a data file. Infers the data format by the file name extension.
     * @param path the input file path.
     * @param format the optional file format specification. For csv files,
     *               it is such as <code>delimiter=\t,header=true,comment=#,escape=\,quote="</code>.
     *               For json files, it is the file mode (single-line or
     *               multi-line). For avro files, it is the path to the schema
     *               file.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame data(String path, String format) throws IOException, URISyntaxException, ParseException {
        int dotIndex = path.lastIndexOf(".");
        String ext = dotIndex < 0 ? "csv" : path.substring(dotIndex + 1);
        switch (ext) {
            case "dat":
            case "txt":
            case "csv": return csv(path, format);
            case "arff": return arff(path);
            case "json":
                JSON.Mode mode = format == null ? JSON.Mode.SINGLE_LINE : JSON.Mode.valueOf(format);
                return json(path, mode, null);
            case "sas7bdat": return sas(path);
            case "avro": return avro(path, format);
            case "parquet": return parquet(path);
            case "feather": return arrow(path);
            default:
                if (format != null) {
                    if (format.equals("csv")) {
                        return csv(path);
                    } else if (format.startsWith("csv,")) {
                        return csv(path, format.substring(4));
                    }
                }
        }

        throw new UnsupportedOperationException("Unsupported data format: " + ext);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame csv(String path) throws IOException, URISyntaxException {
        return csv(path, CSVFormat.DEFAULT);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @param format the format specification in key-value pairs such as
     *               <code>delimiter=\t,header=true,comment=#,escape=\,quote="</code>.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame csv(String path, String format) throws IOException, URISyntaxException {
        CSVFormat csvFormat = CSVFormat.DEFAULT;
        for (String token : format.split(",")) {
            String[] option = token.split("=");
            if (option.length != 2) {
                throw new IllegalArgumentException("Invalid csv format specifier: " + token);
            }
            switch (option[0].toLowerCase(Locale.ROOT)) {
                case "delimiter":
                    csvFormat = csvFormat.withDelimiter(Strings.unescape(option[1]).charAt(0));
                    break;
                case "quote":
                    csvFormat = csvFormat.withQuote(Strings.unescape(option[1]).charAt(0));
                    break;
                case "escape":
                    csvFormat = csvFormat.withEscape(Strings.unescape(option[1]).charAt(0));
                    break;
                case "comment":
                    csvFormat = csvFormat.withCommentMarker(Strings.unescape(option[1]).charAt(0));
                case "header":
                    if (Boolean.parseBoolean(option[1])) {
                        csvFormat = csvFormat.withFirstRecordAsHeader();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown csv format specifier: " + option[0]);
            }
        }

        return csv(path, csvFormat);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @param format the CSV file format.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame csv(String path, CSVFormat format) throws IOException, URISyntaxException {
        return csv(path, format, null);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @param format the CSV file format.
     * @param schema the data schema.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame csv(String path, CSVFormat format, StructType schema) throws IOException, URISyntaxException {
        CSV csv = new CSV(format);
        if (schema != null) csv.schema(schema);
        return csv.read(path);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame csv(Path path) throws IOException {
        return csv(path, CSVFormat.DEFAULT);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @param format the CSV file format.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame csv(Path path, CSVFormat format) throws IOException {
        return csv(path, format, null);
    }

    /**
     * Reads a CSV file.
     * @param path the input file path.
     * @param format the CSV file format.
     * @param schema the data schema.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame csv(Path path, CSVFormat format, StructType schema) throws IOException {
        CSV csv = new CSV(format);
        if (schema != null) csv.schema(schema);
        return csv.read(path);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame json(String path) throws IOException, URISyntaxException {
        return json(path, JSON.Mode.SINGLE_LINE, null);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @param mode the file mode (single-line or multi-line).
     * @param schema the data schema.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame json(String path, JSON.Mode mode, StructType schema) throws IOException, URISyntaxException {
        JSON json = new JSON().mode(mode);
        if (schema != null) json.schema(schema);
        return json.read(path);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame json(Path path) throws IOException {
        return json(path, JSON.Mode.SINGLE_LINE, null);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @param mode the file mode (single-line or multi-line).
     * @param schema the data schema.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame json(Path path, JSON.Mode mode, StructType schema) throws IOException {
        JSON json = new JSON().mode(mode);
        if (schema != null) json.schema(schema);
        return json.read(path);
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
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame arff(String path) throws IOException, ParseException, URISyntaxException {
        Arff arff = new Arff(path);
        return arff.read();
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
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     * @return the data frame.
     */
    static DataFrame arff(Path path) throws IOException, ParseException {
        Arff arff = new Arff(path);
        return arff.read();
    }

    /**
     * Reads a SAS7BDAT file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame sas(String path) throws IOException, URISyntaxException {
        return SAS.read(path);
    }

    /**
     * Reads a SAS7BDAT file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame sas(Path path) throws IOException {
        return SAS.read(path);
    }

    /**
     * Reads an Apache Arrow file.
     * Apache Arrow is a cross-language development platform for in-memory data.
     * It specifies a standardized language-independent columnar memory format
     * for flat and hierarchical data, organized for efficient analytic
     * operations on modern hardware.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame arrow(String path) throws IOException, URISyntaxException {
        Arrow arrow = new Arrow();
        return arrow.read(path);
    }

    /**
     * Reads an Apache Arrow file.
     * Apache Arrow is a cross-language development platform for in-memory data.
     * It specifies a standardized language-independent columnar memory format
     * for flat and hierarchical data, organized for efficient analytic
     * operations on modern hardware.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame arrow(Path path) throws IOException {
        Arrow arrow = new Arrow();
        return arrow.read(path);
    }

    /**
     * Reads an Apache Avro file.
     *
     * @param path the input file path.
     * @param schema the input stream of data schema.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame avro(String path, InputStream schema) throws IOException, URISyntaxException {
        Avro avro = new Avro(schema);
        return avro.read(path);
    }

    /**
     * Reads an Apache Avro file.
     *
     * @param path the input file path.
     * @param schema the data schema file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame avro(String path, String schema) throws IOException, URISyntaxException {
        Avro avro = new Avro(HadoopInput.stream(schema));
        return avro.read(path);
    }

    /**
     * Reads an Apache Avro file.
     *
     * @param path the input file path.
     * @param schema the input stream of data schema.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame avro(Path path, InputStream schema) throws IOException {
        Avro avro = new Avro(schema);
        return avro.read(path);
    }

    /**
     * Reads an Apache Avro file.
     *
     * @param path the input file path.
     * @param schema the data schema file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame avro(Path path, Path schema) throws IOException {
        Avro avro = new Avro(schema);
        return avro.read(path);
    }

    /**
     * Reads an Apache Parquet file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame parquet(String path) throws IOException, URISyntaxException {
        return Parquet.read(path);
    }

    /**
     * Reads an Apache Parquet file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static DataFrame parquet(Path path) throws IOException {
        return Parquet.read(path);
    }

    /**
     * Reads a libsvm sparse dataset. The format of libsvm file is:
     * <pre>
     * {@code
     * <label> <index1>:<value1> <index2>:<value2> ...
     * }</pre>
     * where {@code label} is the target value of the training data.
     * For classification, it should be an integer which identifies a class
     * (multi-class classification is supported). For regression, it's any real
     * number. For one-class SVM, it's not used so can be any number.
     * {@code index} is an integer starting from 1, and {@code value}
     * is a real number. The indices must be in an ascending order. The labels in
     * the testing data file are only used to calculate accuracy or error. If they
     * are unknown, just fill this column with a number.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static Dataset<Instance<SparseArray>> libsvm(String path) throws IOException, URISyntaxException {
        return libsvm(Input.reader(path));
    }

    /**
     * Reads a libsvm sparse dataset. The format of libsvm file is:
     * <pre>
     * {@code
     * <label> <index1>:<value1> <index2>:<value2> ...
     * }</pre>
     * where {@code label} is the target value of the training data.
     * For classification, it should be an integer which identifies a class
     * (multi-class classification is supported). For regression, it's any real
     * number. For one-class SVM, it's not used so can be any number.
     * {@code index} is an integer starting from 1, and {@code value}
     * is a real number. The indices must be in an ascending order. The labels in
     * the testing data file are only used to calculate accuracy or error. If they
     * are unknown, just fill this column with a number.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static Dataset<Instance<SparseArray>> libsvm(Path path) throws IOException {
        return libsvm(Files.newBufferedReader(path));
    }

    /**
     * Reads a libsvm sparse dataset. The format of libsvm file is:
     * <pre>
     * {@code
     * <label> <index1>:<value1> <index2>:<value2> ...
     * }</pre>
     * where {@code label} is the target value of the training data.
     * For classification, it should be an integer which identifies a class
     * (multi-class classification is supported). For regression, it's any real
     * number. For one-class SVM, it's not used so can be any number.
     * {@code index} is an integer starting from 1, and {@code value}
     * is a real number. The indices must be in an ascending order. The labels in
     * the testing data file are only used to calculate accuracy or error. If they
     * are unknown, just fill this column with a number.
     *
     * @param reader the file reader.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    static Dataset<Instance<SparseArray>> libsvm(BufferedReader reader) throws IOException {
        try {
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
                        final int y = Integer.parseInt(firstToken);
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
                        final double y = Double.parseDouble(firstToken);
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
        } finally {
            reader.close();
        }
    }    
}
