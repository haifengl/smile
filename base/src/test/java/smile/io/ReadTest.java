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

import org.junit.jupiter.api.BeforeEach;
import smile.data.DataFrame;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ReadTest {

    public ReadTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testReadDataArff() throws Exception {
        System.out.println("arff");
        DataFrame weather = Read.data(Paths.getTestData("weka/weather.nominal.arff").toString());
        System.out.println(weather);
        assertEquals(14, weather.nrow());
        assertEquals(5, weather.ncol());
    }

    @Test
    public void testReadDataAvro() throws Exception {
        System.out.println("avro");
        DataFrame df = Read.data(
                Paths.getTestData("kylo/userdata1.avro").toString(),
                Paths.getTestData("kylo/userdata.avsc").toString()
        );
        System.out.println(df);
        assertEquals(1000, df.nrow());
        assertEquals(13, df.ncol());
    }

    @Test
    public void testReadDataAvroEmbeddedSchema() throws Exception {
        System.out.println("avro embedded schema");
        DataFrame df = Read.data(Paths.getTestData("kylo/userdata1.avro"));
        assertEquals(1000, df.nrow());
        assertEquals(13, df.ncol());
    }

    @Test
    public void testReadDataTsv() throws Exception {
        System.out.println("tsv");
        var temp = java.nio.file.Files.createTempFile("smile-test-", ".tsv");
        try {
            java.nio.file.Files.writeString(temp, "name\tage\nAlice\t30\nBob\t25\n");
            DataFrame df = Read.data(temp);
            assertEquals(2, df.nrow());
            assertEquals(2, df.ncol());
            assertEquals("Alice", df.getString(0, 0));
            assertEquals(30, df.getInt(0, 1));
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    @Test
    public void testReadDataJsonl() throws Exception {
        System.out.println("jsonl");
        var temp = java.nio.file.Files.createTempFile("smile-test-", ".jsonl");
        try {
            java.nio.file.Files.copy(
                    Paths.getTestData("kylo/books.json"),
                    temp,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            DataFrame df = Read.data(temp);
            assertEquals(7, df.nrow());
            assertEquals(10, df.ncol());
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    @Test
    public void testReadDataArrow() throws Exception {
        System.out.println("arrow");
        DataFrame weather = Read.arff(Paths.getTestData("weka/weather.nominal.arff"));
        var temp = java.nio.file.Files.createTempFile("smile-test-", ".arrow");
        try {
            Write.arrow(weather, temp);
            DataFrame df = Read.data(temp);
            assertEquals(weather.nrow(), df.nrow());
            assertEquals(weather.ncol(), df.ncol());
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    @Test
    public void testReadDataJson() throws Exception {
        System.out.println("json");
        DataFrame df = Read.data(Paths.getTestData("kylo/books.json").toString());
        System.out.println(df);
        assertEquals(7, df.nrow());
        assertEquals(10, df.ncol());
    }

    @Test
    public void testReadDataSas() throws Exception {
        System.out.println("sas");
        DataFrame df = Read.data(Paths.getTestData("sas/airline.sas7bdat").toString());
        System.out.println(df);
        assertEquals(32, df.nrow());
        assertEquals(6, df.ncol());
    }

    @Test
    public void testReadDataParquet() throws Exception {
        System.out.println("parquet");
        String path = Paths.getTestData("kylo/userdata1.parquet").toAbsolutePath().toString();
        DataFrame df = Read.data(path);
        System.out.println(df);
        assertEquals(1000, df.nrow());
        assertEquals(13, df.ncol());
    }

    @Test
    public void testReadDataCsvZip() throws Exception {
        System.out.println("csv zip");
        DataFrame usps = Read.data(Paths.getTestData("usps/zip.train").toString(), "csv,delimiter= ");
        System.out.println(usps);
        assertEquals(7291, usps.nrow());
        assertEquals(257, usps.ncol());
    }

    @Test
    public void testReadDataCsvGdp() throws Exception {
        System.out.println("csv gdp");
        DataFrame gdp = Read.data(Paths.getTestData("regression/gdp.csv").toString(), "header=true,comment=%");
        System.out.println(gdp);
        assertEquals(68, gdp.nrow());
        assertEquals(4, gdp.ncol());
    }
}
