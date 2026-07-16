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
package smile.data;

import java.sql.SQLException;
import java.util.Map;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SQL}, including SQL-injection regression tests.
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

    // -----------------------------------------------------------------------
    // requireSafeIdentifier – allow-list validation
    // -----------------------------------------------------------------------

    @Test
    public void testSafeIdentifierAcceptsSimpleName() {
        System.out.println("requireSafeIdentifier – simple name");
        // Given / When / Then
        assertEquals("my_table", SQL.requireSafeIdentifier("my_table"));
        assertEquals("T1",       SQL.requireSafeIdentifier("T1"));
        assertEquals("_hidden",  SQL.requireSafeIdentifier("_hidden"));
    }

    @Test
    public void testSafeIdentifierRejectsSpaces() {
        System.out.println("requireSafeIdentifier – space in name");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier("my table"));
    }

    @Test
    public void testSafeIdentifierRejectsSemicolon() {
        System.out.println("requireSafeIdentifier – semicolon stacking");
        // Classic stacked-statement injection: "t; DROP TABLE users--"
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier("t; DROP TABLE users--"));
    }

    @Test
    public void testSafeIdentifierRejectsQuote() {
        System.out.println("requireSafeIdentifier – quote-breaking attempt");
        // Attempt to break out of a quoted identifier context
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier("t' OR '1'='1"));
    }

    @Test
    public void testSafeIdentifierRejectsDash() {
        System.out.println("requireSafeIdentifier – SQL comment sequence");
        // "--" starts a SQL comment; a name containing it is malicious
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier("t--comment"));
    }

    @Test
    public void testSafeIdentifierRejectsLeadingDigit() {
        System.out.println("requireSafeIdentifier – leading digit");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier("1table"));
    }

    @Test
    public void testSafeIdentifierRejectsNull() {
        System.out.println("requireSafeIdentifier – null input");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier(null));
    }

    @Test
    public void testSafeIdentifierRejectsEmpty() {
        System.out.println("requireSafeIdentifier – empty string");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeIdentifier(""));
    }

    // -----------------------------------------------------------------------
    // escape – single-quote doubling
    // -----------------------------------------------------------------------

    @Test
    public void testEscapeDoublesQuotes() {
        System.out.println("escape – single-quote doubling");
        // Given a path containing a single-quote (quote-breaking attempt)
        // When
        String escaped = SQL.escape("file'with'quotes.csv");
        // Then no bare single-quote remains
        assertEquals("file''with''quotes.csv", escaped);
    }

    @Test
    public void testEscapeNullReturnsEmpty() {
        System.out.println("escape – null returns empty string");
        assertEquals("", SQL.escape(null));
    }

    // -----------------------------------------------------------------------
    // requireSafeLiteral – dangerous character rejection
    // -----------------------------------------------------------------------

    @Test
    public void testSafeLiteralRejectsSemicolon() {
        System.out.println("requireSafeLiteral – semicolon stacking");
        // A path ending with "; DROP TABLE users" is rejected
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file.csv; DROP TABLE users"));
    }

    @Test
    public void testSafeLiteralRejectsLineTerminator() {
        System.out.println("requireSafeLiteral – newline payload");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file.csv\nDROP TABLE users"));
    }

    @Test
    public void testSafeLiteralRejectsCarriageReturn() {
        System.out.println("requireSafeLiteral – carriage-return payload");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file.csv\rEVIL"));
    }

    @Test
    public void testSafeLiteralRejectsSqlLineComment() {
        System.out.println("requireSafeLiteral – SQL line-comment sequence");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file.csv'--"));
    }

    @Test
    public void testSafeLiteralRejectsBlockComment() {
        System.out.println("requireSafeLiteral – SQL block-comment sequence");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file.csv'/*evil*/"));
    }

    @Test
    public void testSafeLiteralRejectsNulByte() {
        System.out.println("requireSafeLiteral – NUL byte truncation attack");
        assertThrows(IllegalArgumentException.class,
                () -> SQL.requireSafeLiteral("/data/file\u0000.csv"));
    }

    @Test
    public void testSafeLiteralAcceptsNormalPath() {
        System.out.println("requireSafeLiteral – normal file path accepted");
        // A typical file path with spaces, dots, slashes, and hyphens is fine
        String path = "/home/user/data-files/my_data.csv";
        assertEquals(path, SQL.requireSafeLiteral(path));
    }

    @Test
    public void testSafeLiteralAcceptsWindowsPath() {
        System.out.println("requireSafeLiteral – Windows path accepted");
        String path = "C:\\\\Users\\\\data\\\\file.csv";
        assertEquals(path, SQL.requireSafeLiteral(path));
    }

    // -----------------------------------------------------------------------
    // csv() – injection through table name and path
    // -----------------------------------------------------------------------

    @Test
    public void testCsvRejectsInjectedTableName() throws SQLException {
        System.out.println("csv() – injected table name is rejected");
        // Given an in-memory SQL instance
        try (SQL sql = new SQL()) {
            // When / Then – table name contains a semicolon (stacked statement)
            assertThrows(IllegalArgumentException.class,
                    () -> sql.csv("t; DROP TABLE users--", "/some/file.csv"));
        }
    }

    @Test
    public void testCsvRejectsPathWithSemicolon() throws SQLException {
        System.out.println("csv() – path with semicolon is rejected");
        try (SQL sql = new SQL()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sql.csv("mytable", "/path/to/file.csv; DROP TABLE mytable"));
        }
    }

    @Test
    public void testCsvRejectsPathWithNewline() throws SQLException {
        System.out.println("csv() – path with newline is rejected");
        try (SQL sql = new SQL()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sql.csv("mytable", "/path/to/file.csv\nDROP TABLE mytable"));
        }
    }

    @Test
    public void testCsvColumnListRejectsInjectedKey() throws SQLException {
        System.out.println("csv() – columnList key with semicolon is rejected");
        try (SQL sql = new SQL()) {
            Map<String, String> cols = Map.of("col; DROP TABLE x--", "VARCHAR");
            assertThrows(IllegalArgumentException.class,
                    () -> sql.csv("mytable", ',', cols, "/some/file.csv"));
        }
    }

    @Test
    public void testCsvColumnListRejectsInjectedValue() throws SQLException {
        System.out.println("csv() – columnList value with comment sequence is rejected");
        try (SQL sql = new SQL()) {
            Map<String, String> cols = Map.of("col", "VARCHAR'--");
            assertThrows(IllegalArgumentException.class,
                    () -> sql.csv("mytable", ',', cols, "/some/file.csv"));
        }
    }

    // -----------------------------------------------------------------------
    // parquet() – injection through table name
    // -----------------------------------------------------------------------

    @Test
    public void testParquetRejectsInjectedTableName() throws SQLException {
        System.out.println("parquet() – injected table name is rejected");
        try (SQL sql = new SQL()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sql.parquet("t' OR '1'='1", "/some/file.parquet"));
        }
    }

    // -----------------------------------------------------------------------
    // json() – injection through format string
    // -----------------------------------------------------------------------

    @Test
    public void testJsonRejectsInjectedFormat() throws SQLException {
        System.out.println("json() – injected format string is rejected");
        try (SQL sql = new SQL()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sql.json("mytable", "auto'; DROP TABLE mytable--",
                            (Map<String, String>) null, "/some/file.json"));
        }
    }

    // -----------------------------------------------------------------------
    // optionList – injection through option key and value
    // -----------------------------------------------------------------------

    @Test
    public void testOptionListRejectsInjectedKey() throws SQLException {
        System.out.println("parquet() option – unsafe key is rejected");
        try (SQL sql = new SQL()) {
            // option key must satisfy SAFE_IDENTIFIER (letters/digits/underscores)
            Map<String, String> opts = Map.of("opt; DROP TABLE x", "true");
            assertThrows(IllegalArgumentException.class,
                    () -> sql.parquet("mytable", opts, "/some/file.parquet"));
        }
    }

    @Test
    public void testOptionListRejectsInjectedValue() throws SQLException {
        System.out.println("parquet() option – unsafe value is rejected");
        try (SQL sql = new SQL()) {
            Map<String, String> opts = Map.of("hive_partitioning", "true\nDROP TABLE x");
            assertThrows(IllegalArgumentException.class,
                    () -> sql.parquet("mytable", opts, "/some/file.parquet"));
        }
    }
}