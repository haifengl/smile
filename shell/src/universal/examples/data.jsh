//--- CELL ---
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import smile.data.*;
import smile.data.vector.*;
import smile.io.*;
import smile.math.MathEx;
import smile.util.Index;

// A ValueVector that is a one-dimensional labeled abstraction
// to store a sequence of values having the same type.
var now = Instant.now();
var a = ValueVector.of("A", 1.0, 2.0, 3.0);
var b = ValueVector.of("B", now, now.minus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS));
var c = ValueVector.nominal("C", "test", "train", "validation");
var d = ValueVector.of("D", "string vector", "Hello World", "Nominal/ordinal vectors store data as integers internally");
var e = ObjectVector.of("E", Index.range(0, 4).toArray(), new int[]{4, 4, 4, 4}, Index.range(4, 8).toArray());

// DataFrame is a two-dimensional data structure like a table
// with rows and columns. Each column is a ValueVector.
var df = new DataFrame(a, b, c, d, e);

// The method schema() will describe the column names,
// data types, whether they can be null.
var schema = df.schema();
//--- CELL ---
// We create a DataFrame with a 2-dimensional array.
var rand = DataFrame.of(MathEx.randn(6, 4));

// We can create a DataFrame with a collection of records or beans.
enum Gender {
	Male,
	Female
}

record Person(
	String name,
	Gender gender,
	String state,
	LocalDate birthday,
	int age,
	Double salary) { }

List<Person> persons = new ArrayList<>();
persons.add(new Person("Alex", Gender.Male, "NY", LocalDate.of(1980, 10, 1), 38, 10000.));
persons.add(new Person("Bob", Gender.Male, "AZ", LocalDate.of(1995, 3, 4), 23, null));
persons.add(new Person("Jane", Gender.Female, "CA", LocalDate.of(1970, 3, 1), 48, 230000.));
persons.add(new Person("Amy", Gender.Female, "NY", LocalDate.of(2005, 12, 10), 13, null));
var employees = DataFrame.of(Person.class, persons);

// The column 'state' is of string type. Apparently, it is a categorical variable.
// We can factorize such string columns to categorical values.
var cat = employees.factorize("state");
//--- CELL ---
// Smile provides a couple of parsers for popular data formats,
// such as Parquet, Avro, Arrow, SAS7BDAT, Weka's ARFF files,
// LibSVM's file format, delimited text files, JSON, and
// binary sparse data. 
var home = System.getProperty("smile.home");
var iris = Read.arff(home + "/data/weka/iris.arff");
var summary = iris.describe();

// Parquet
var users = Read.parquet(Paths.getTestData("kylo/userdata1.parquet"));

// Avro
Avro avro = new Avro(Paths.getTestData("kylo/userdata.avsc"));
var users1 = avro.read(Paths.getTestData("kylo/userdata1.avro"));

// SAS
var airline = Read.sas(home + "/data/sas/airline.sas7bdat");

// Advanced operations such as exists, forall, find, filter are also supported.
IO.println(iris.stream().anyMatch(row -> row.getDouble(0) > 4.5));
IO.println(iris.stream().allMatch(row -> row.getDouble(0) < 10));
IO.println(iris.stream().filter(row -> row.getByte("class") == 1).findAny());
IO.println(iris.stream().filter(row -> row.getString("class").equals("Iris-versicolor")).findAny());
//--- CELL ---
// For data wrangling, the most important functions of DataFrame are map and groupBy.
var x6 = iris.stream().map(row -> {
               var x = new double[6];
               for (int i = 0; i < 4; i++) x[i] = row.getDouble(i);
               x[4] = x[0] * x[1];
               x[5] = x[2] * x[3];
               return x;
           });

x6.forEach(xi -> System.out.println(Arrays.toString(xi)));

var classes = iris.stream().collect(java.util.stream.Collectors.groupingBy(row -> row.getString("class")));
