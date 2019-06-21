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

package smile.data;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.matrix.DenseMatrix;
import smile.util.Paths;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class DataFrameJDBCTest {

    DataFrame df;

    public DataFrameJDBCTest() {
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
        } catch (SQLException ex) {
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
        assertEquals("Jane", df.get(0).getString(0));
        assertEquals("Peacock", df.get(0).getString(1));
        assertEquals("Luís", df.get(0).getString(2));
        assertEquals("Gonçalves", df.get(0).getString(3));
        assertEquals("Brazil", df.get(0).getString(4));
        assertEquals(3.98, df.get(0).getDouble(5), 1E-10);

        assertEquals("Steve", df.get(7).getString(0));
        assertEquals("Johnson", df.get(7).getString(1));
        assertEquals("Leonie", df.get(7).getString(2));
        assertEquals("Köhler", df.get(7).getString(3));
        assertEquals("Germany", df.get(7).getString(4));
        assertEquals(1.98, df.get(7).getDouble(5), 1E-10);
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