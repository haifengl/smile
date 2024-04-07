/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.sql.SQLException;
import smile.util.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SQLTest {

    public SQLTest() {

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
    public void test() throws SQLException {
        System.out.println("SQL");
        SQL sql = new SQL();
        sql.openParquet("user", Paths.getTestData("kylo/userdata1.parquet").toString());
        sql.openJSON("books", Paths.getTestData("kylo/books_array.json").toString());
        sql.openCSV("gdp", Paths.getTestData("regression/gdp.csv").toString());
        sql.openCSV("diabetes", Paths.getTestData("regression/diabetes.csv").toString());

        DataFrame tables = sql.tables();
        System.out.println(tables);
        assertEquals(4, tables.nrow());

        DataFrame columns = sql.describe("user");
        System.out.println(columns.toString(100));
        assertEquals(13, columns.nrow());

        columns = sql.describe("books");
        System.out.println(columns.toString(100));
        assertEquals(10, columns.nrow());

        columns = sql.describe("gdp");
        System.out.println(columns.toString(100));
        assertEquals(4, columns.nrow());

        columns = sql.describe("diabetes");
        System.out.println(columns.toString(100));
        assertEquals(65, columns.nrow());

        DataFrame user = sql.query("SELECT * FROM user");
        assertEquals(1000, user.nrow());
        assertEquals(13, user.ncol());

        DataFrame join = sql.query("SELECT * FROM user LEFT JOIN gdp ON user.country = gdp.Country");
        System.out.println(join.toString(100));
        assertEquals(user.nrow(), join.nrow());
        assertEquals(17, join.ncol());
        sql.close();
    }
}