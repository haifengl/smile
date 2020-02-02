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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.matrix.DenseMatrix;
import smile.util.Paths;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ArrowTest {

    Arrow arrow = new Arrow();
    DataFrame df;

    public ArrowTest() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }

        String url = String.format("jdbc:sqlite:%s", Paths.getTestData("sqlite/chinook.db").toAbsolutePath());
        String sql = "select e.firstname as 'Employee First', e.lastname as 'Employee Last', c.firstname as 'Customer First', c.lastname as 'Customer Last', c.country, i.total"
                + " from employees as e"
                + " join customers as c on e.employeeid = c.supportrepid"
                + " join invoices as i on c.customerid = i.customerid";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {
            df = DataFrame.of(rs);
            File temp = File.createTempFile("chinook", "arrow");
            Path path = temp.toPath();
            arrow.write(df, path);
            df = arrow.read(path);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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


    /**
     * Test of nrows method, of class DataFrame.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(412, df.nrows());
    }

    /**
     * Test of ncols method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(6, df.ncols());
    }

    /**
     * Test of schema method, of class DataFrame.
     */
    @Test
    public void testSchema() {
        System.out.println("schema");
        System.out.println(df.schema());
        System.out.println(df.structure());
        System.out.println(df);
        smile.data.type.StructType schema = DataTypes.struct(
                new StructField("Employee First", DataTypes.StringType),
                new StructField("Employee Last", DataTypes.StringType),
                new StructField("Customer First", DataTypes.StringType),
                new StructField("Customer Last", DataTypes.StringType),
                new StructField("Country", DataTypes.StringType),
                new StructField("Total", DataTypes.DoubleObjectType)
        );
        assertEquals(schema, df.schema());
    }

    /**
     * Test of get method, of class DataFrame.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        System.out.println(df.get(0));
        System.out.println(df.get(1));
        assertEquals("Jane", df.getString(0, 0));
        assertEquals("Peacock", df.getString(0, 1));
        assertEquals("Luís", df.getString(0, 2));
        assertEquals("Gonçalves", df.getString(0, 3));
        assertEquals("Brazil", df.getString(0, 4));
        assertEquals(3.98, df.getDouble(0, 5), 1E-10);

        assertEquals("Steve", df.getString(7, 0));
        assertEquals("Johnson", df.getString(7, 1));
        assertEquals("Leonie", df.getString(7, 2));
        assertEquals("Köhler", df.getString(7, 3));
        assertEquals("Germany", df.getString(7, 4));
        assertEquals(1.98, df.getDouble(7, 5), 1E-10);
    }

    /**
     * Test of summary method, of class DataFrame.
     */
    @Test
    public void testDataFrameSummary() {
        System.out.println("summary");
        DataFrame output = df.summary();
        System.out.println(output);
        System.out.println(output.schema());
        assertEquals(1, output.nrows());
        assertEquals(5, output.ncols());
        assertEquals("Total", output.get(0,0));
        assertEquals(412L, output.get(0,1));
        assertEquals(0.99, output.get(0,2));
        assertEquals(5.651941747572815, output.get(0,3));
        assertEquals(25.86, output.get(0,4));
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToMatrix() {
        System.out.println("toMatrix");
        DenseMatrix output = df.select("Total").toMatrix();
        System.out.println(output);
        assertEquals(412, output.nrows());
        assertEquals(1, output.ncols());
        assertEquals(3.98, output.get(0, 0), 1E-10);
        assertEquals(3.96, output.get(1, 0), 1E-10);
        assertEquals(5.94, output.get(2, 0), 1E-10);
        assertEquals(0.99, output.get(3, 0), 1E-10);
    }
}