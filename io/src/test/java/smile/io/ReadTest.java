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

import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.util.Paths;

/**
 *
 * @author Haifeng Li
 */
public class ReadTest {

    public ReadTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testReadDataArff() throws Exception {
        System.out.println("arff");
        DataFrame df = Read.data(Paths.getTestData("weka/weather.nominal.arff").toString());
        System.out.println(df);
    }

    @Test
    public void testReadDataAvro() throws Exception {
        System.out.println("avro");
        DataFrame df = Read.data(
                Paths.getTestData("avro/userdata1.avro").toString(),
                Paths.getTestData("avro/userdata.avsc").toString()
        );
        System.out.println(df);
    }

    @Test
    public void testReadDataJson() throws Exception {
        System.out.println("json");
        DataFrame df = Read.data(Paths.getTestData("json/books1.json").toString());
        System.out.println(df);
    }

    @Test
    public void testReadDataSas() throws Exception {
        System.out.println("sas");
        DataFrame df = Read.data(Paths.getTestData("sas/airline.sas7bdat").toString());
        System.out.println(df);
    }

    @Test
    public void testReadDataParquet() throws Exception {
        System.out.println("parquet");
        DataFrame df = Read.data(Paths.getTestData("parquet/userdata1.parquet").toString());
        System.out.println(df);
    }

    @Test
    public void testReadDataCsvZip() throws Exception {
        System.out.println("csv zip");
        DataFrame df = Read.data(Paths.getTestData("usps/zip.train").toString(), "csv?");
        System.out.println(df);
    }

    @Test
    public void testReadDataCsvGdp() throws Exception {
        System.out.println("csv gdp");
        DataFrame df = Read.data(Paths.getTestData("regression/gdp.csv").toString(), "header=true,comment=%");
        System.out.println(df);
    }
}
