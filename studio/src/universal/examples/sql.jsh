import smile.data.SQL;
import smile.data.DataFrame;

SQL sql = new SQL();
var home = System.getProperty("smile.home");
sql.parquet("user", home + "/data/kylo/userdata1.parquet");
sql.json("books", home + "/data/kylo/books_array.json");
sql.csv("gdp", home + "/data/regression/gdp.csv");
sql.csv("diabetes", home + "/data/regression/diabetes.csv");

var tables = sql.tables();
var columns = sql.describe("user");

var user = sql.query("SELECT * FROM user WHERE country = 'China'");
var gdp = sql.query("SELECT * FROM user LEFT JOIN gdp ON user.country = gdp.Country");
