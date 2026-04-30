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

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import smile.util.Strings;

/**
 * An in-process SQL database management interface.
 *
 * @author Haifeng Li
 */
public class SQL implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SQL.class);

    /**
     * Allowed SQL identifier pattern: letters, digits, and underscores,
     * must start with a letter or underscore.  Rejects any character that
     * could break out of an identifier context (quotes, semicolons, spaces,
     * dashes, slashes, comment markers, etc.).
     */
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Characters that must never appear inside a single-quoted SQL string
     * literal even after {@code '} is doubled.  Semicolons can stack
     * statements; comment sequences can suppress trailing syntax; NUL bytes
     * are used in truncation attacks; line-terminators can escape some
     * single-line comment defenses.
     */
    private static final Pattern DANGEROUS_IN_LITERAL =
            Pattern.compile("[;\\x00\\n\\r]|--|/\\*");

    /** JDBC connection. */
    private final Connection db;

    static {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException ex) {
            logger.error("Failed to load DuckDB driver: ", ex);
        }
    }

    /**
     * Constructor of in-memory database.
     * @throws SQLException if fail to create an in-memory database.
     */
    public SQL() throws SQLException {
        db = DriverManager.getConnection("jdbc:duckdb:");
    }

    /**
     * Constructor to open or create a persistent database.
     * @param path DuckDB file path.
     * @throws SQLException if fail to open or create the persistent database.
     */
    public SQL(String path) throws SQLException {
        db = DriverManager.getConnection("jdbc:duckdb:" + path);
    }

    @Override
    public String toString() {
        try {
            return String.format("SQL(%s)", db.getCatalog());
        } catch (SQLException ex) {
            return "SQL(memory)";
        }
    }

    @Override
    public void close() throws SQLException {
        db.close();
    }

    /**
     * Returns the installed extensions.
     * @return the installed extensions.
     * @throws SQLException if fail to execute the SQL query.
     */
    public DataFrame extensions() throws SQLException {
        return query("""
                SELECT extension_name AS name, loaded, description,
                       extension_version AS version, install_mode AS mode
                FROM duckdb_extensions() WHERE installed = true;""");
    }

    /**
     * Updates the extensions.
     *
     * @return this object.
     * @throws SQLException if fail to update the extensions.
     */
    public SQL updateExtensions() throws SQLException {
        execute("UPDATE EXTENSIONS;");
        return this;
    }

    /**
     * Installs an extension.
     *
     * @param name the extension name.
     * @return this object.
     * @throws SQLException if fail to install the extension.
     */
    public SQL installExtension(String name) throws SQLException {
        execute("INSTALL " + requireSafeIdentifier(name) + ';');
        return this;
    }

    /**
     * Loads an extension.
     *
     * @param name the extension name.
     * @return this object.
     * @throws SQLException if fail to load the extension.
     */
    public SQL loadExtension(String name) throws SQLException {
        execute("LOAD " + requireSafeIdentifier(name) + ';');
        return this;
    }

    /**
     * Returns the tables in the database.
     * @return the data frame of table metadata.
     * @throws SQLException if fail to query metadata.
     */
    public DataFrame tables() throws SQLException {
        return query("""
                SELECT table_name
                FROM duckdb_tables()
                WHERE NOT internal;""");
    }

    /**
     * Returns the columns in a table.
     * @param table the table name.
     * @return the data frame of table columns.
     * @throws SQLException if fail to query metadata.
     */
    public DataFrame describe(String table) throws SQLException {
        DatabaseMetaData meta = db.getMetaData();
        try (var rs = meta.getColumns(null, null, table, null)) {
            DataFrame df = DataFrame.of(rs);
            return df.select("COLUMN_NAME", "TYPE_NAME", "IS_NULLABLE");
        }
    }

    /**
     * Creates an in-memory table from csv files. The file should have a
     * header line and uses the default comma delimiter. You can read a
     * series of files and treat them as if they were a single table.
     * Note that this only works if the files have the same schema.
     * The files can be specified by a list parameter, glob pattern
     * matching syntax, or a combination of both.
     *
     * @param name the table name.
     * @param path the csv file path.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL csv(String name, String... path) throws SQLException {
        return csv(name, ',', null, path);
    }

    /**
     * Creates an in-memory table from csv files. You can read a series
     * of files and treat them as if they were a single table. Note that
     * this only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     *
     * @param name the table name.
     * @param path a list of csv files.
     * @param delimiter the delimiter character that separates columns.
     * @param columns a map that specifies the column names and column types.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL csv(String name, char delimiter, Map<String, String> columns, String... path) throws SQLException {
        // Reject delimiter characters that could break the SQL literal.
        String delimStr = String.valueOf(delimiter);
        if (DANGEROUS_IN_LITERAL.matcher(delimStr).find() || delimiter == '\'') {
            throw new IllegalArgumentException(
                    "Delimiter character is not allowed inside a SQL literal: " + delimiter);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                CREATE TABLE %s AS
                SELECT * FROM read_csv(%s,delim='%c',""",
                requireSafeIdentifier(name), fileList(path), delimiter));
        if (columns == null) {
            sb.append("header=true");
        } else {
            sb.append("columns=");
            sb.append(columnList(columns));
        }
        sb.append(')');

        String query = sb.toString();
        logger.info(query);
        try (var stmt = db.createStatement()) {
            stmt.execute(query);
        }
        return this;
    }

    /**
     * Creates an in-memory table from iceberg tables.
     *
     * @param name the table name.
     * @param path the path to iceberg table folder.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL iceberg(String name, String path) throws SQLException {
        return iceberg(name, path, false);
    }

    /**
     * Creates an in-memory table from iceberg tables.
     *
     * @param name the table name.
     * @param path the path to iceberg table folder.
     * @param allowMovedPaths the flag whether allows scanning Iceberg tables that are moved.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL iceberg(String name, String path, boolean allowMovedPaths) throws SQLException {
        String query = String.format("""
                CREATE TABLE %s AS
                SELECT * FROM iceberg_scan('%s', allow_moved_paths = %s);""",
                requireSafeIdentifier(name),
                requireSafeLiteral(escape(path)),
                allowMovedPaths);
        logger.info(query);
        try (var stmt = db.createStatement()) {
            stmt.execute(query);
        }
        return this;
    }

    /**
     * Creates an in-memory table from parquet files. You can read a series
     * of files and treat them as if they were a single table. Note that
     * this only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     *
     * @param name the table name.
     * @param path a list of parquet files.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL parquet(String name, String... path) throws SQLException {
        return parquet(name, null, path);
    }

    /**
     * Creates an in-memory table from parquet files. You can read a series
     * of files and treat them as if they were a single table. Note that
     * this only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     *
     * @param name the table name.
     * @param path a list of parquet files.
     * @param options supported options include
     *               'binary_as_string' - Parquet files generated by legacy writers
     *               do not correctly set the UTF8 flag for strings, causing string
     *               columns to be loaded as BLOB instead. Set this to true to load
     *               binary columns as strings.
     *               'filename' - Whether an extra filename column should be included
     *               in the result.
     *               'file_row_number' - Whether to include the file_row_number column.
     *               'hive_partitioning' - Whether to interpret the path as a
     *               Hive partitioned path.
     *               'union_by_name' - Whether the columns of multiple schemas should be
     *               unified by name, rather than by position.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL parquet(String name, Map<String, String> options, String... path) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                CREATE TABLE %s AS
                SELECT * FROM read_parquet(%s""",
                requireSafeIdentifier(name), fileList(path)));
        if (options != null) {
            sb.append(", ");
            sb.append(optionList(options));
        }
        sb.append(')');

        String query = sb.toString();
        logger.info(query);
        try (var stmt = db.createStatement()) {
            stmt.execute(query);
        }
        return this;
    }

    /**
     * Creates an in-memory table from JSON files. You can read a series of
     * files and treat them as if they were a single table. Note that this
     * only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     * @param name the table name.
     * @param path a list of JSON files.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL json(String name, String... path) throws SQLException {
        return json(name, "auto", null, path);
    }

    /**
     * Creates an in-memory table from JSON files. You can read a series of
     * files and treat them as if they were a single table. Note that this
     * only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     * @param name the table name.
     * @param path a list of JSON files.
     * @param format "auto", "unstructured", "newline_delimited", or "array".
     *               "auto" - Attempt to determine the format automatically.
     *               "newline_delimited" - Each line is a JSON.
     *               "array" - A JSON array of objects (pretty-printed or not).
     *               "unstructured" - If the JSON file contains JSON that is
     *               not newline-delimited or an array.
     * @param columns a map that specifies the column names and column types.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL json(String name, String format, Map<String, String> columns, String... path) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                CREATE TABLE %s AS
                SELECT * FROM read_json(%s, format = '%s'""",
                requireSafeIdentifier(name), fileList(path),
                requireSafeLiteral(escape(format))));
        if (columns != null) {
            sb.append(", columns = ");
            sb.append(columnList(columns));
        }
        sb.append(')');

        String query = sb.toString();
        logger.info(query);
        try (var stmt = db.createStatement()) {
            stmt.execute(query);
        }
        return this;
    }

    /**
     * Returns the string representation of a list of file paths.
     * Each path is escaped (single-quote doubled) and then validated to
     * contain no statement-separator or comment meta-characters.
     *
     * @param path a list of file paths.
     * @return the string representation safe for embedding in SQL text.
     * @throws IllegalArgumentException if any path contains a dangerous
     *         character that cannot be safely represented in a SQL literal.
     */
    private String fileList(String... path) {
        return Arrays.stream(path)
                .map(s -> "'" + requireSafeLiteral(escape(s)) + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Returns the string representation of column specification.
     * Both the column name key and type value are escaped and validated.
     *
     * @param columns a map that specifies the column names and column types.
     * @return the string representation safe for embedding in SQL text.
     * @throws IllegalArgumentException if any key or value contains dangerous
     *         characters.
     */
    private String columnList(Map<String, String> columns) {
        return columns.keySet().stream()
                .map(key -> String.format("'%s': '%s'",
                        requireSafeLiteral(escape(key)),
                        requireSafeLiteral(escape(columns.get(key)))))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Returns the string representation of an option map.
     * Option keys must be safe SQL identifiers; values are escaped and
     * validated as safe SQL literals.
     *
     * @param options a map of options.
     * @return the string representation safe for embedding in SQL text.
     * @throws IllegalArgumentException if any key is not a safe identifier or
     *         any value contains dangerous characters.
     */
    private String optionList(Map<String, String> options) {
        return options.keySet().stream()
                .map(key -> String.format("%s = '%s'",
                        requireSafeIdentifier(key),
                        requireSafeLiteral(escape(options.get(key)))))
                .collect(Collectors.joining(", "));
    }

    /**
     * Validates that {@code name} is a safe SQL identifier (letters, digits,
     * and underscores only, must start with a letter or underscore).
     *
     * @param name the identifier to validate.
     * @return {@code name} unchanged if it is safe.
     * @throws IllegalArgumentException if {@code name} contains any character
     *         not allowed in a plain SQL identifier.
     */
    static String requireSafeIdentifier(String name) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Unsafe SQL identifier – only letters, digits and underscores " +
                    "are allowed and the name must start with a letter or underscore: " +
                    name);
        }
        return name;
    }

    /**
     * Escapes a string value for safe embedding inside a SQL single-quoted
     * literal by doubling every single-quote character ({@code '} → {@code ''}).
     *
     * @param value the raw string value.
     * @return the escaped string (without the surrounding quotes).
     */
    static String escape(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    /**
     * Validates that an already-escaped string value does not contain
     * characters that remain dangerous inside a SQL single-quoted literal
     * even after escaping: semicolons ({@code ;}), NUL bytes, line
     * terminators ({@code \n}, {@code \r}), or comment sequences
     * ({@code --}, {@code /*}).
     *
     * @param escaped the value after {@link #escape(String)} has been applied.
     * @return {@code escaped} unchanged if it is safe.
     * @throws IllegalArgumentException if the value contains a dangerous
     *         character sequence.
     */
    static String requireSafeLiteral(String escaped) {
        if (DANGEROUS_IN_LITERAL.matcher(escaped).find()) {
            throw new IllegalArgumentException(
                    "SQL literal value contains a dangerous character sequence " +
                    "(semicolon, NUL byte, line terminator, or comment marker). " +
                    "Value rejected to prevent SQL injection.");
        }
        return escaped;
    }

    /**
     * Exports the database to the target directory as CSV files.
     * @param path the directory path.
     * @throws SQLException if fail to execute the SQL statement.
     */
    public void export(String path) throws SQLException {
        execute(String.format("EXPORT DATABASE '%s' (FORMAT csv);", requireSafeLiteral(escape(path))));
    }

    /**
     * Writes a table to a CSV file. The file will have a header line
     * and uses the default comma delimiter.
     * @param table the table name.
     * @param path the file path.
     * @throws SQLException if fail to execute the SQL statement.
     */
    public void export(String table, String path) throws SQLException {
        execute(String.format("COPY %s TO '%s' (FORMAT csv, DELIMITER ',', HEADER);",
                requireSafeIdentifier(table), requireSafeLiteral(escape(path))));
    }

    /**
     * Write the result of a query to a CSV file. The file will have a header line
     * and uses the default comma delimiter.
     * @param sql a SELECT statement.
     * @param path the file path.
     * @throws SQLException if fail to execute the SQL statement.
     */
    public void query(String sql, String path) throws SQLException {
        execute(String.format("COPY (%s) TO '%s' (FORMAT csv, DELIMITER ',', HEADER);",
                requireNonBlank(sql).replaceAll(";$", ""), requireSafeLiteral(escape(path))));
    }

    /**
     * Executes an ad-hoc SELECT statement.
     *
     * <p>The statement is compiled via {@link Connection#prepareStatement(String)}
     * before execution so that a syntactically invalid statement is rejected with
     * {@link SQLException} before any data is touched.</p>
     *
     * <p>This method is intended for interactive or trusted-caller use. The
     * caller is responsible for ensuring the string is a single, non-malicious
     * {@code SELECT} statement.</p>
     *
     * @param sql a SELECT statement.
     * @return the query result.
     * @throws SQLException if the statement is syntactically invalid or fails
     *         to execute.
     * @throws IllegalArgumentException if {@code sql} is null or blank.
     */
    public DataFrame query(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.prepareStatement(requireNonBlank(sql))) {
            return DataFrame.of(stmt.executeQuery());
        }
    }

    /**
     * Executes an ad-hoc INSERT, UPDATE, or DELETE statement.
     *
     * <p>The statement is compiled via {@link Connection#prepareStatement(String)}
     * before execution so that a syntactically invalid statement is rejected with
     * {@link SQLException} before any rows are modified.</p>
     *
     * <p>This method is intended for interactive or trusted-caller use. The
     * caller is responsible for ensuring the string is a single, non-malicious
     * DML statement.</p>
     *
     * @param sql an INSERT, UPDATE, or DELETE statement.
     * @return the number of rows affected by the SQL statement.
     * @throws SQLException if the statement is syntactically invalid or fails
     *         to execute.
     * @throws IllegalArgumentException if {@code sql} is null or blank.
     */
    public int update(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.prepareStatement(requireNonBlank(sql))) {
            return stmt.executeUpdate();
        }
    }

    /**
     * Executes an ad-hoc SQL statement, which may return multiple results.
     *
     * <p>This method accepts any SQL, including DDL ({@code CREATE}, {@code DROP},
     * etc.). This method is intended for interactive or trusted-caller use. The
     * caller is responsible for ensuring the string is a single, non-malicious
     * SQL statement.</p>
     *
     * @param sql an SQL statement.
     * @return true if the first result is a ResultSet object;
     *         false if it is an update count or there are no results.
     * @throws SQLException if the statement fails to execute.
     * @throws IllegalArgumentException if {@code sql} is null or blank.
     */
    public boolean execute(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.createStatement()) {
            return stmt.execute(requireNonBlank(sql));
        }
    }

    /**
     * Validates that {@code sql} is not {@code null} and not blank.
     *
     * @param sql the SQL string to check.
     * @return {@code sql} unchanged.
     * @throws IllegalArgumentException if {@code sql} is {@code null} or blank.
     */
    private static String requireNonBlank(String sql) {
        if (Strings.isNullOrBlank(sql)) {
            throw new IllegalArgumentException("SQL statement must not be null or blank.");
        }
        return sql;
    }
}
