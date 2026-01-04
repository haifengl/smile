//!mvn org.xerial:sqlite-jdbc:3.51.1.0

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import smile.data.DataFrame;
import smile.io.Paths;

Class.forName("org.sqlite.JDBC");

String url = String.format("jdbc:sqlite:%s", Paths.getTestData("sqlite/chinook.db").toAbsolutePath());
String sql = """
             select e.firstname as 'Employee First', e.lastname as 'Employee Last',
                    c.firstname as 'Customer First', c.lastname as 'Customer Last', c.country, i.total
             from employees as e
             join customers as c on e.employeeid = c.supportrepid
             join invoices as i on c.customerid = i.customerid""";

Connection conn = DriverManager.getConnection(url);
Statement stmt  = conn.createStatement();
ResultSet rs    = stmt.executeQuery(sql);
var df = DataFrame.of(rs);
