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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An in-process SQL database management interface.
 *
 * @author Haifeng Li
 */
public class SQL implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SQL.class);
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
        execute("INSTALL " + name + ';');
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
        execute("LOAD " + name + ';');
        return this;
    }

    /**
     * Returns the tables in the database.
     * @return the data frame of table metadata.
     * @throws SQLException if fail to query metadata.
     */
    public DataFrame tables() throws SQLException {
        DatabaseMetaData meta = db.getMetaData();
        try (var rs = meta.getTables(null, null, null, null)) {
            DataFrame df = DataFrame.of(rs);
            return df.select("TABLE_NAME", "REMARKS");
        }
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
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                CREATE TABLE %s AS
                SELECT * FROM read_csv(%s,delim='%c',""",
                name, fileList(path), delimiter));
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
                name, path, allowMovedPaths);
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
                name, fileList(path)));
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
     * Creates an in-memory table from json files. You can read a series of
     * files and treat them as if they were a single table. Note that this
     * only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     * @param name the table name.
     * @param path a list of json files.
     * @return this object.
     * @throws SQLException if fail to read the files or create the in-memory table.
     */
    public SQL json(String name, String... path) throws SQLException {
        return json(name, "auto", null, path);
    }

    /**
     * Creates an in-memory table from json files. You can read a series of
     * files and treat them as if they were a single table. Note that this
     * only works if the files have the same schema. The files can be
     * specified by a list parameter, glob pattern matching syntax, or
     * a combination of both.
     * @param name the table name.
     * @param path a list of json files.
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
                name, fileList(path), format));
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
     * @param path a list of file paths.
     * @return the string representation.
     */
    private String fileList(String... path) {
        return Arrays.stream(path)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Returns the string representation of column specification.
     * @param columns a map that specifies the column names and column types.
     * @return the string representation.
     */
    private String columnList(Map<String, String> columns) {
        return columns.keySet().stream()
                .map(key -> String.format("'%s': '%s'", key, columns.get(key)))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Returns the string representation of an option map.
     * @param options a map of options.
     * @return the string representation.
     */
    private String optionList(Map<String, String> options) {
        return options.keySet().stream()
                .map(key -> String.format("%s = %s", key, options.get(key)))
                .collect(Collectors.joining(", "));

    }

    /**
     * Executes a SELECT statement.
     * @param sql a SELECT statement.
     * @return the query result.
     * @throws SQLException if fail to execute the SQL query.
     */
    public DataFrame query(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.createStatement()) {
            return DataFrame.of(stmt.executeQuery(sql));
        }
    }

    /**
     * Executes an INSERT, UPDATE, or DELETE statement.
     * @param sql an INSERT, UPDATE, or DELETE statement.
     * @return the number of rows affected by the SQL statement.
     * @throws SQLException if fail to execute the SQL update.
     */
    public int update(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * Executes an SQL statement, which may return multiple results.
     * @param sql an SQL statement.
     * @return true if the first result is a ResultSet object;
     *         false if it is an update count or there are no results.
     * @throws SQLException if fail to execute the SQL query.
     */
    public boolean execute(String sql) throws SQLException {
        logger.info(sql);
        try (var stmt = db.createStatement()) {
            return stmt.execute(sql);
        }
    }
}
