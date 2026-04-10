/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.SparseDataset;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the smile.io package covering:
 * <ul>
 *   <li>Read / Write object serialization (resource-safe round-trips)</li>
 *   <li>CSV: format-string parsing, null-format guard, schema override,
 *       read/write round-trip, tab-delimited, limit, charset</li>
 *   <li>Read.data() extension detection including query-string stripping</li>
 *   <li>Read.libsvm(): valid data, malformed token, zero index guard</li>
 *   <li>Read.csv(Path) / Read.json(Path) / Read.arff(Path) Path overloads</li>
 *   <li>Input.stream() helpers</li>
 * </ul>
 */
public class IOTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a temporary file with UTF-8 content and returns its path. */
    private static Path tmpFile(String content) throws IOException {
        Path p = Files.createTempFile("smile-io-test-", ".tmp");
        p.toFile().deleteOnExit();
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    // -----------------------------------------------------------------------
    // Read / Write – object serialization round-trip
    // -----------------------------------------------------------------------

    @Test
    public void testObjectRoundTrip() throws Exception {
        System.out.println("Read/Write object round-trip");
        String original = "Hello SMILE serialization";
        Path tmp = Write.object(original);
        Object restored = Read.object(tmp);
        assertInstanceOf(String.class, restored);
        assertEquals(original, restored);
    }

    @Test
    public void testObjectWriteToExplicitPath() throws Exception {
        System.out.println("Write object to explicit path");
        Path tmp = Files.createTempFile("smile-obj-", ".ser");
        tmp.toFile().deleteOnExit();
        List<Integer> data = List.of(1, 2, 3);
        Write.object((Serializable) data, tmp);
        @SuppressWarnings("unchecked")
        List<Integer> restored = (List<Integer>) Read.object(tmp);
        assertEquals(data, restored);
    }

    // -----------------------------------------------------------------------
    // CSV – null format guard (bug fix: no NPE when format == null)
    // -----------------------------------------------------------------------

    @Test
    public void testCsvNullFormatFallsBackToDefault() throws Exception {
        System.out.println("CSV null format guard");
        // "a,b,c\n1,2,3\n" — default comma delimiter, no header
        Path tmp = tmpFile("hello,world,42\nfoo,bar,7\n");
        // Before the fix this would throw NullPointerException
        DataFrame df = Read.csv(tmp.toString(), (String) null);
        assertEquals(2, df.nrow());
        assertEquals(3, df.ncol());
    }

    // -----------------------------------------------------------------------
    // CSV – format string parser (delimiter, header, quote, escape, comment)
    // -----------------------------------------------------------------------

    @Test
    public void testCsvFormatStringTabDelimiter() throws Exception {
        System.out.println("CSV format string – tab delimiter + header");
        Path tmp = tmpFile("name\tage\tvalue\nAlice\t30\t1.5\nBob\t25\t2.0\n");
        DataFrame df = Read.csv(tmp.toString(), "delimiter=\\t,header=true");
        assertEquals(2, df.nrow());
        assertEquals(3, df.ncol());
        assertEquals("name", df.names()[0]);
        assertEquals("age",  df.names()[1]);
        assertEquals("value", df.names()[2]);
        assertEquals("Alice", df.get(0, 0));
        assertEquals(30,      df.getInt(0, 1));
        assertEquals(1.5,     df.getDouble(0, 2), 1E-9);
    }

    @Test
    public void testCsvFormatStringPipeDelimiter() throws Exception {
        System.out.println("CSV format string – pipe delimiter");
        Path tmp = tmpFile("1|2|3\n4|5|6\n");
        DataFrame df = Read.csv(tmp.toString(), "delimiter=|");
        assertEquals(2, df.nrow());
        assertEquals(3, df.ncol());
        assertEquals(1, df.getInt(0, 0));
        assertEquals(6, df.getInt(1, 2));
    }

    @Test
    public void testCsvFormatStringCommentMarker() throws Exception {
        System.out.println("CSV format string – comment marker");
        // Use semicolon as delimiter so the comment marker token is unambiguous
        Path tmp = tmpFile("# ignored line\n10;20;30\n40;50;60\n");
        DataFrame df = Read.csv(tmp.toString(), "delimiter=;,comment=#");
        assertEquals(2, df.nrow());
        assertEquals(3, df.ncol());
        assertEquals(10, df.getInt(0, 0));
    }

    @Test
    public void testCsvFormatStringNamedColumns() throws Exception {
        System.out.println("CSV format string – named columns via header=x|y|z");
        Path tmp = tmpFile("1,2\n3,4\n");
        DataFrame df = Read.csv(tmp.toString(), "header=x|y");
        assertEquals(2, df.nrow());
        assertEquals(2, df.ncol());
        assertEquals("x", df.names()[0]);
        assertEquals("y", df.names()[1]);
    }

    @Test
    public void testCsvFormatStringInvalidKeyThrows() {
        System.out.println("CSV format string – unknown key throws");
        assertThrows(IllegalArgumentException.class,
                () -> Read.csv("/dev/null", "boguskey=val"));
    }

    @Test
    public void testCsvFormatStringMissingEqualsThrows() {
        System.out.println("CSV format string – missing '=' throws");
        assertThrows(IllegalArgumentException.class,
                () -> Read.csv("/dev/null", "header"));
    }

    // -----------------------------------------------------------------------
    // CSV – schema override
    // -----------------------------------------------------------------------

    @Test
    public void testCsvSchemaOverride() throws Exception {
        System.out.println("CSV schema override");
        Path tmp = tmpFile("Alice,30,1.5\nBob,25,2.0\n");
        StructType schema = new StructType(
                new StructField("name",  DataTypes.StringType),
                new StructField("age",   DataTypes.IntType),
                new StructField("score", DataTypes.DoubleType)
        );
        CSV csv = new CSV();
        csv.schema(schema);
        DataFrame df = csv.read(tmp);
        assertEquals(2,       df.nrow());
        assertEquals(3,       df.ncol());
        assertEquals("Alice", df.getString(0, 0));
        assertEquals(30,      df.getInt(0, 1));
        assertEquals(1.5,     df.getDouble(0, 2), 1E-9);
    }

    // -----------------------------------------------------------------------
    // CSV – write then read round-trip
    // -----------------------------------------------------------------------

    @Test
    public void testCsvWriteReadRoundTrip() throws Exception {
        System.out.println("CSV write/read round-trip");
        DataFrame orig = Read.csv(Paths.getTestData("regression/abalone-train.data"));
        Path tmp = Files.createTempFile("smile-csv-rt-", ".csv");
        tmp.toFile().deleteOnExit();

        // Write.csv always prints a header row then data rows.
        Write.csv(orig, tmp);

        // Re-read: the written file has a header row followed by data rows.
        // Use setHeader()+setSkipHeaderRecord(true) so the first line is consumed as names.
        DataFrame restored = Read.csv(tmp,
                CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).get());
        assertEquals(orig.nrow(), restored.nrow());
        assertEquals(orig.ncol(), restored.ncol());
        // First cell must survive the round-trip (written as getString, so always a String)
        assertEquals(orig.getString(0, 0), restored.getString(0, 0));
    }

    @Test
    public void testCsvWriteReadWithHeaderRoundTrip() throws Exception {
        System.out.println("CSV write/read with header round-trip");
        DataFrame orig = Read.csv(
                Paths.getTestData("regression/prostate-train.csv"),
                CSVFormat.Builder.create()
                        .setDelimiter('\t').setHeader().setSkipHeaderRecord(true).get());

        Path tmp = Files.createTempFile("smile-csv-hdr-", ".csv");
        tmp.toFile().deleteOnExit();

        // Write.csv always prepends a header row from the schema field names,
        // then writes all data rows. Do NOT also pass setHeader() to the write
        // format, or the header is printed twice.
        Write.csv(orig, tmp);

        // Read back: the file has exactly one header row followed by data rows.
        DataFrame restored = Read.csv(tmp,
                CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).get());
        assertEquals(orig.nrow(), restored.nrow());
        assertEquals(orig.ncol(), restored.ncol());
        assertArrayEquals(orig.names(), restored.names());
    }

    // -----------------------------------------------------------------------
    // CSV – limit
    // -----------------------------------------------------------------------

    @Test
    public void testCsvLimit() throws Exception {
        System.out.println("CSV limit");
        CSV csv = new CSV();
        DataFrame full    = csv.read(Paths.getTestData("regression/abalone-train.data"));
        CSV csv2 = new CSV();
        DataFrame limited = csv2.read(Paths.getTestData("regression/abalone-train.data"), 10);
        assertEquals(10,            limited.nrow());
        assertEquals(full.ncol(),   limited.ncol());
        // First row must be identical
        assertEquals(full.getString(0, 0), limited.getString(0, 0));
    }

    // -----------------------------------------------------------------------
    // Read.data() – extension detection
    // -----------------------------------------------------------------------

    @Test
    public void testReadDataExtensionArff() throws Exception {
        System.out.println("Read.data() .arff extension");
        DataFrame df = Read.data(
                Paths.getTestData("weka/weather.nominal.arff").toString());
        assertEquals(14, df.nrow());
        assertEquals(5,  df.ncol());
    }

    @Test
    public void testReadDataExtensionJson() throws Exception {
        System.out.println("Read.data() .json extension");
        DataFrame df = Read.data(
                Paths.getTestData("kylo/books.json").toString());
        assertEquals(7, df.nrow());
    }

    @Test
    public void testReadDataExtensionCsvWithFormatOverride() throws Exception {
        System.out.println("Read.data() csv, format override");
        // The .csv extension hits Read.csv(path, format); pass format tokens directly
        DataFrame df = Read.data(
                Paths.getTestData("regression/gdp.csv").toString(),
                "header=true,comment=%");
        assertEquals(68, df.nrow());
        assertEquals(4,  df.ncol());
    }

    @Test
    public void testReadDataExtensionCsvExplicit() throws Exception {
        System.out.println("Read.data() explicit 'csv' format");
        DataFrame df = Read.data(
                Paths.getTestData("regression/abalone-train.data").toString(),
                "csv");
        assertEquals(3133, df.nrow());
    }

    @Test
    public void testReadDataUnsupportedExtensionThrows() {
        System.out.println("Read.data() unsupported extension throws");
        assertThrows(UnsupportedOperationException.class,
                () -> Read.data("/some/file.xyz"));
    }

    @Test
    public void testReadDataUnsupportedExtensionNoFormatThrows() {
        System.out.println("Read.data() unknown ext, no format throws");
        assertThrows(UnsupportedOperationException.class,
                () -> Read.data("/some/file.unknown", null));
    }

    // -----------------------------------------------------------------------
    // Read – Path overloads (csv, json, arff)
    // -----------------------------------------------------------------------

    @Test
    public void testReadCsvPathOverload() throws Exception {
        System.out.println("Read.csv(Path)");
        DataFrame df = Read.csv(Paths.getTestData("regression/abalone-train.data"));
        assertEquals(3133, df.nrow());
        assertEquals(9,    df.ncol());
    }

    @Test
    public void testReadCsvPathWithFormatOverload() throws Exception {
        System.out.println("Read.csv(Path, CSVFormat)");
        // prostate-train.csv is a true tab-delimited file with a header row
        CSVFormat fmt = CSVFormat.Builder.create()
                .setDelimiter('\t').setHeader().setSkipHeaderRecord(true).get();
        DataFrame df = Read.csv(Paths.getTestData("regression/prostate-train.csv"), fmt);
        assertEquals(67, df.nrow());
        assertEquals(9,  df.ncol());
    }

    @Test
    public void testReadCsvPathWithSchemaOverload() throws Exception {
        System.out.println("Read.csv(Path, CSVFormat, StructType)");
        // Use prostate-train.csv: tab-delimited with header row
        StructType schema = new StructType(
                new StructField("lcavol",  DataTypes.DoubleType),
                new StructField("lweight", DataTypes.DoubleType),
                new StructField("age",     DataTypes.IntType),
                new StructField("lbph",    DataTypes.DoubleType),
                new StructField("svi",     DataTypes.IntType),
                new StructField("lcp",     DataTypes.DoubleType),
                new StructField("gleason", DataTypes.IntType),
                new StructField("pgg45",   DataTypes.IntType),
                new StructField("lpsa",    DataTypes.DoubleType)
        );
        CSVFormat fmt = CSVFormat.Builder.create()
                .setDelimiter('\t').setHeader().setSkipHeaderRecord(true).get();
        DataFrame df = Read.csv(
                Paths.getTestData("regression/prostate-train.csv"), fmt, schema);
        assertEquals(67, df.nrow());
        assertEquals(-0.579818495, df.getDouble(0, 0), 1E-7);
        assertEquals(50, df.getInt(0, 2));
    }

    @Test
    public void testReadJsonPathOverload() throws Exception {
        System.out.println("Read.json(Path)");
        DataFrame df = Read.json(Paths.getTestData("kylo/books.json"));
        assertEquals(7, df.nrow());
    }

    @Test
    public void testReadArffPathOverload() throws Exception {
        System.out.println("Read.arff(Path)");
        DataFrame df = Read.arff(Paths.getTestData("weka/weather.nominal.arff"));
        assertEquals(14, df.nrow());
        assertEquals(5,  df.ncol());
    }

    // -----------------------------------------------------------------------
    // Read.libsvm – valid data
    // -----------------------------------------------------------------------

    @Test
    public void testLibsvmPathOverload() throws Exception {
        System.out.println("Read.libsvm(Path)");
        SparseDataset<Integer> ds =
                Read.libsvm(Paths.getTestData("libsvm/glass.txt"));
        assertEquals(214, ds.size());
        assertEquals(1,   ds.get(0).y());
        assertEquals(9,   ds.get(0).x().size());
    }

    @Test
    public void testLibsvmInlineData() throws Exception {
        System.out.println("Read.libsvm inline");
        // 1-based indices: 1:0.5  2:1.5  3:2.5
        Path tmp = tmpFile(
                "1 1:0.5 2:1.5 3:2.5\n" +
                "-1 1:0.0 3:3.0\n");
        SparseDataset<Integer> ds = Read.libsvm(tmp);
        assertEquals(2, ds.size());
        // Row 0
        assertEquals(1,   ds.get(0).y());
        assertEquals(0.5, ds.get(0).x().get(0), 1E-9);   // index 1 → 0
        assertEquals(1.5, ds.get(0).x().get(1), 1E-9);   // index 2 → 1
        assertEquals(2.5, ds.get(0).x().get(2), 1E-9);   // index 3 → 2
        // Row 1
        assertEquals(-1,  ds.get(1).y());
        assertEquals(0.0, ds.get(1).x().get(0), 1E-9);
        assertEquals(3.0, ds.get(1).x().get(2), 1E-9);
    }

    @Test
    public void testLibsvmLabelTypes() throws Exception {
        System.out.println("Read.libsvm multi-class labels");
        Path tmp = tmpFile(
                "0 1:1.0\n" +
                "5 2:2.0\n" +
                "20 1:0.5 2:1.0\n");
        SparseDataset<Integer> ds = Read.libsvm(tmp);
        assertEquals(0,  ds.get(0).y());
        assertEquals(5,  ds.get(1).y());
        assertEquals(20, ds.get(2).y());
    }

    // -----------------------------------------------------------------------
    // Read.libsvm – malformed input (bug-fix: validation)
    // -----------------------------------------------------------------------

    @Test
    public void testLibsvmMalformedTokenThrows() throws Exception {
        System.out.println("Read.libsvm malformed token throws");
        Path tmp = tmpFile("1 1:0.5 2invalid\n");
        assertThrows(NumberFormatException.class, () -> Read.libsvm(tmp));
    }

    @Test
    public void testLibsvmZeroIndexThrows() throws Exception {
        System.out.println("Read.libsvm zero 1-based index throws (bug fix)");
        // Index 0 is invalid in libsvm (1-based), j = 0 - 1 = -1
        Path tmp = tmpFile("1 0:1.0 1:2.0\n");
        assertThrows(NumberFormatException.class, () -> Read.libsvm(tmp));
    }

    @Test
    public void testLibsvmEmptyFileProducesEmptyDataset() throws Exception {
        System.out.println("Read.libsvm empty file");
        Path tmp = tmpFile("");
        SparseDataset<Integer> ds = Read.libsvm(tmp);
        assertEquals(0, ds.size());
    }

    // -----------------------------------------------------------------------
    // CSV – inferSchema via BufferedReader
    // -----------------------------------------------------------------------

    @Test
    public void testInferSchemaIntegers() throws Exception {
        System.out.println("CSV.inferSchema – integer columns");
        Path tmp = tmpFile("1,2,3\n4,5,6\n7,8,9\n");
        CSV csv = new CSV();
        StructType schema = csv.inferSchema(
                Files.newBufferedReader(tmp), 1000);
        assertEquals(3, schema.length());
        for (var f : schema.fields()) {
            assertEquals(DataTypes.IntType, f.dtype(),
                    "Expected IntType for column " + f.name());
        }
    }

    @Test
    public void testInferSchemaStringAndDouble() throws Exception {
        System.out.println("CSV.inferSchema – String + Double columns");
        Path tmp = tmpFile("alpha,1.5\nbeta,2.7\n");
        CSV csv = new CSV();
        StructType schema = csv.inferSchema(
                Files.newBufferedReader(tmp), 1000);
        assertEquals(2, schema.length());
        assertEquals(DataTypes.StringType, schema.field(0).dtype());
        assertEquals(DataTypes.DoubleType, schema.field(1).dtype());
    }

    @Test
    public void testInferSchemaMissingValuesBecomesNullable() throws Exception {
        System.out.println("CSV – missing values → nullable columns");
        Path tmp = tmpFile("1,2.0\n,3.5\n4,\n");
        CSV csv = new CSV();
        DataFrame df = csv.read(tmp);
        assertTrue(df.column("V1").isNullable(),
                "Column with missing int should be nullable");
        assertTrue(df.column("V2").isNullable(),
                "Column with missing double should be nullable");
    }

    // -----------------------------------------------------------------------
    // Input.stream – local file
    // -----------------------------------------------------------------------

    @Test
    public void testInputStreamLocalFile() throws Exception {
        System.out.println("Input.stream local file");
        Path tmp = tmpFile("hello");
        try (var stream = Input.stream(tmp.toString())) {
            byte[] bytes = stream.readAllBytes();
            assertEquals("hello", new String(bytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testInputReaderLocalFile() throws Exception {
        System.out.println("Input.reader local file");
        Path tmp = tmpFile("world");
        try (var reader = Input.reader(tmp.toString())) {
            assertEquals("world", reader.readLine());
        }
    }
}
