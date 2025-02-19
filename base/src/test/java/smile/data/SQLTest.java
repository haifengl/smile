/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.sql.SQLException;
import smile.io.Paths;
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
        sql.parquet("user", Paths.getTestData("kylo/userdata1.parquet").toString());
        sql.json("books", Paths.getTestData("kylo/books_array.json").toString());
        sql.csv("gdp", Paths.getTestData("regression/gdp.csv").toString());
        sql.csv("diabetes", Paths.getTestData("regression/diabetes.csv").toString());

        DataFrame tables = sql.tables();
        System.out.println(tables);
        assertEquals(4, tables.size());

        DataFrame columns = sql.describe("user");
        System.out.println(columns.head(100));
        assertEquals(13, columns.size());

        columns = sql.describe("books");
        System.out.println(columns.head(100));
        assertEquals(10, columns.size());

        columns = sql.describe("gdp");
        System.out.println(columns.head(100));
        assertEquals(4, columns.size());

        columns = sql.describe("diabetes");
        System.out.println(columns.head(100));
        assertEquals(65, columns.size());

        DataFrame user = sql.query("SELECT * FROM user");
        assertEquals(1000, user.size());
        assertEquals(13, user.columns().size());

        DataFrame join = sql.query("SELECT * FROM user LEFT JOIN gdp ON user.country = gdp.Country");
        System.out.println(join.head(100));
        assertEquals(user.size(), join.size());
        assertEquals(17, join.columns().size());
        sql.close();
    }
}