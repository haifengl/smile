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
package smile.data.formula;

import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import smile.data.DataFrame;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.io.Paths;
import smile.tensor.Matrix;
import static smile.data.formula.Terms.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FormulaTest {

    public enum Gender {
        Male,
        Female
    }

    public static class Person {
        String name;
        Gender gender;
        LocalDate birthday;
        int age;
        Double salary;
        Person(String name, Gender gender, LocalDate birthday, int age, Double salary) {
            this.name = name;
            this.gender = gender;
            this.birthday = birthday;
            this.age = age;
            this.salary = salary;
        }

        public String getName() { return name; }
        public Gender getGender() { return gender; }
        public LocalDate getBirthday() { return birthday; }
        public int getAge() { return age; }
        public Double getSalary() { return salary; }
    }

    DataFrame df;
    DataFrame weather;

    public FormulaTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", Gender.Male, LocalDate.of(1980, 10, 1), 38, 10000.));
        persons.add(new Person("Bob", Gender.Male, LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", Gender.Female, LocalDate.of(1970, 3, 1), 48, 230000.));
        persons.add(new Person("Amy", Gender.Female, LocalDate.of(2005, 12, 10), 13, null));

        df = DataFrame.of(Person.class, persons);
        System.out.println(df);
        try {
            weather = Read.arff(Paths.getTestData("weka/weather.nominal.arff"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
    public void testToString() {
        System.out.println("toString");
        Formula formula = Formula.lhs("salary");
        assertEquals("salary ~ .", formula.toString());
        assertEquals(formula, Formula.of(formula.toString()));

        formula = Formula.rhs($("salary"));
        assertEquals(" ~ salary", formula.toString());
        assertEquals(formula, Formula.of(formula.toString()));

        formula = Formula.of("salary", dot(), cross("a", "b", "c") , delete("d"));
        assertEquals("salary ~ . + (a x b x c) - d", formula.toString());
        assertEquals(formula, Formula.of(formula.toString()));
    }

    @Test
    public void testDot() {
        System.out.println("dot operator");
        Formula formula = Formula.of("salary", dot(), log("age"), $("gender"));
        assertEquals("salary ~ . + log(age) + gender", formula.toString());

        Formula expanded = formula.expand(df.schema());
        System.out.println(expanded);
        assertEquals("salary ~ age + birthday + name + log(age) + gender", expanded.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);

        StructType schema = new StructType(
                new StructField("salary", DataTypes.NullableDoubleType),
                new StructField("age", DataTypes.IntType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("name", DataTypes.StringType),
                new StructField("log(age)", DataTypes.DoubleType),
                new StructField("gender", DataTypes.ByteType, new NominalScale("Male", "Female"))
        );
        assertEquals(schema, output.schema());
    }

    @Test
    public void testFormula() {
        System.out.println("formula");
        Formula formula = Formula.of("salary", $("age"));
        assertEquals("salary ~ age", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(2, output.shape(1));

        smile.data.type.StructType schema = new StructType(
                new StructField("salary", DataTypes.NullableDoubleType),
                new StructField("age", DataTypes.IntType)
        );
        assertEquals(schema, output.schema());

        DataFrame x = formula.x(df);
        System.out.println(x);
        assertEquals(df.size(), x.size());
        assertEquals(1, x.shape(1));

        smile.data.type.StructType xschema = new StructType(
                new StructField("age", DataTypes.IntType)
        );
        assertEquals(xschema, x.schema());

        assertEquals(10000.0, formula.y(df.get(0)), 1E-7);
        assertEquals(Double.NaN, formula.y(df.get(1)), 1E-7);

        Matrix matrix = formula.matrix(df);
        assertEquals(df.size(), matrix.nrow());
        assertEquals(2, matrix.ncol());
    }

    @Test
    public void testInteraction() {
        System.out.println("interaction");

        Formula formula = Formula.rhs(interact("water", "sowing_density", "wind"));
        StructType inputSchema = new StructType(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong"))
        );
        assertEquals(" ~ water:sowing_density:wind", formula.toString());

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = new StructType(
                new StructField(
                        "water:sowing_density:wind",
                        DataTypes.IntType,
                        new NominalScale(
                                "dry:low:weak", "dry:low:strong", "dry:high:weak", "dry:high:strong",
                                "wet:low:weak", "wet:low:strong", "wet:high:weak", "wet:high:strong")
                )
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testCrossing() {
        System.out.println("crossing");

        Formula formula = Formula.rhs(cross(2, "water", "sowing_density", "wind"));
        StructType inputSchema = new StructType(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong"))
        );
        assertEquals(" ~ (water x sowing_density x wind)^2", formula.toString());

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = new StructType(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong")),
                new StructField("water:sowing_density", DataTypes.IntType, new NominalScale("dry:low", "dry:high", "wet:low", "wet:high")),
                new StructField("water:wind", DataTypes.IntType, new NominalScale("dry:weak", "dry:strong", "wet:weak", "wet:strong")),
                new StructField("sowing_density:wind", DataTypes.IntType, new NominalScale("low:weak", "low:strong", "high:weak", "high:strong"))
        );
        assertEquals(schema, outputSchema);

        formula = Formula.rhs(cross("water", "sowing_density", "wind"));
        assertEquals(" ~ (water x sowing_density x wind)", formula.toString());

        outputSchema = formula.bind(inputSchema);
        schema = new StructType(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong")),
                new StructField("water:sowing_density", DataTypes.IntType, new NominalScale("dry:low", "dry:high", "wet:low", "wet:high")),
                new StructField("water:wind", DataTypes.IntType, new NominalScale("dry:weak", "dry:strong", "wet:weak", "wet:strong")),
                new StructField("sowing_density:wind", DataTypes.IntType, new NominalScale("low:weak", "low:strong", "high:weak", "high:strong")),
                new StructField("water:sowing_density:wind", DataTypes.IntType, new NominalScale("dry:low:weak", "dry:low:strong", "dry:high:weak", "dry:high:strong", "wet:low:weak", "wet:low:strong", "wet:high:weak", "wet:high:strong"))
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testBind() {
        System.out.println("bind");

        Formula formula = Formula.of("revenue", dot(), cross("water", "sowing_density") , mul("humidity", "wind"), delete("wind"));
        StructType inputSchema = new StructType(
                new StructField("revenue", DataTypes.DoubleType, Measure.Currency),
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("humidity", DataTypes.FloatType, Measure.Percent),
                new StructField("wind", DataTypes.FloatType)
        );
        assertEquals("revenue ~ . + (water x sowing_density) + (humidity * wind) - wind", formula.toString());
        System.out.println(formula.expand(inputSchema));

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = new StructType(
                new StructField("humidity", DataTypes.FloatType, Measure.Percent),
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("water:sowing_density", DataTypes.IntType, new NominalScale("dry:low", "dry:high", "wet:low", "wet:high")),
                new StructField("humidity * wind", DataTypes.FloatType)
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testFormulaDate() {
        System.out.println("date");
        Formula formula = Formula.rhs(date("birthday", DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH, DateFeature.DAY_OF_WEEK));
        assertEquals(" ~ birthday[YEAR, MONTH, DAY_OF_MONTH, DAY_OF_WEEK]", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output.schema());
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.shape(1));
        assertEquals(1980, output.get(0,0));
        assertEquals(10, output.get(0,1));
        assertEquals(1, output.get(0,2));
        assertEquals(3, output.get(0,3));
        assertEquals(1970, output.get(2,0));
        assertEquals(3, output.get(2,1));
        assertEquals(1, output.get(2,2));
        assertEquals(7, output.get(2,3));
    }

    @Test
    public void testFormulaAbs() {
        System.out.println("abs");
        Formula formula = Formula.rhs(abs("age"));
        assertEquals(" ~ abs(age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.abs(38), output.get(0,0));
        assertEquals(Math.abs(23), output.get(1,0));
        assertEquals(Math.abs(48), output.get(2,0));
        assertEquals(Math.abs(13), output.get(3,0));
    }

    @Test
    public void testFormulaAbsNullable() {
        System.out.println("abs null");
        Formula formula = Formula.rhs(abs("salary"));
        assertEquals(" ~ abs(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.abs(10000.), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.abs(230000.), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaExp() {
        System.out.println("exp");
        Formula formula = Formula.rhs(exp("age"));
        assertEquals(" ~ exp(age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.exp(38), output.get(0,0));
        assertEquals(Math.exp(23), output.get(1,0));
        assertEquals(Math.exp(48), output.get(2,0));
        assertEquals(Math.exp(13), output.get(3,0));
    }

    @Test
    public void testFormulaExpNullable() {
        System.out.println("exp null");
        Formula formula = Formula.rhs(exp(div("salary", val(10000))));
        assertEquals(" ~ exp(salary / 10000)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.exp(1), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.exp(23), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaLog() {
        System.out.println("log");
        Formula formula = Formula.rhs(log("age"));
        assertEquals(" ~ log(age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.log(38), output.get(0,0));
        assertEquals(Math.log(23), output.get(1,0));
        assertEquals(Math.log(48), output.get(2,0));
        assertEquals(Math.log(13), output.get(3,0));
    }

    @Test
    public void testFormulaLogNullable() {
        System.out.println("log null");
        Formula formula = Formula.rhs(log("salary"));
        assertEquals(" ~ log(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.log(10000), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.log(230000), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaLog10() {
        System.out.println("log10");
        Formula formula = Formula.rhs(log10("age"));
        assertEquals(" ~ log10(age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.log10(38), output.get(0,0));
        assertEquals(Math.log10(23), output.get(1,0));
        assertEquals(Math.log10(48), output.get(2,0));
        assertEquals(Math.log10(13), output.get(3,0));
    }

    @Test
    public void testFormulaLog10Nullable() {
        System.out.println("log10 null");
        Formula formula = Formula.rhs(log10("salary"));
        assertEquals(" ~ log10(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.log10(10000), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.log10(230000), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaSqrt() {
        System.out.println("sqrt");
        Formula formula = Formula.rhs(sqrt("age"));
        assertEquals(" ~ sqrt(age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.sqrt(38), output.get(0,0));
        assertEquals(Math.sqrt(23), output.get(1,0));
        assertEquals(Math.sqrt(48), output.get(2,0));
        assertEquals(Math.sqrt(13), output.get(3,0));
    }

    @Test
    public void testFormulaSqrtNullable() {
        System.out.println("sqrt null");
        Formula formula = Formula.rhs(sqrt("salary"));
        assertEquals(" ~ sqrt(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.sqrt(10000), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.sqrt(230000), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaCeilNullable() {
        System.out.println("ceil null");
        Formula formula = Formula.rhs(ceil("salary"));
        assertEquals(" ~ ceil(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(10000.0, output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(230000.0, output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaFloorNullable() {
        System.out.println("floor null");
        Formula formula = Formula.rhs(floor("salary"));
        assertEquals(" ~ floor(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(10000.0, output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(230000.0, output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaRoundNullable() {
        System.out.println("round null");
        Formula formula = Formula.rhs(round("salary"));
        assertEquals(" ~ round(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(10000.0, output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(230000.0, output.get(2,0));
        assertNull(output.get(3, 0));

        Matrix matrix = formula.matrix(df);
        System.out.println(matrix);
        assertEquals(df.size(), matrix.nrow());
        assertEquals(2, matrix.ncol());
        assertEquals(1, matrix.get(0,0), 1E-10);
        assertEquals(1, matrix.get(1,0), 1E-10);
        assertEquals(1, matrix.get(2,0), 1E-10);
        assertEquals(1, matrix.get(3,0), 1E-10);
        assertEquals(Math.round(10000.), matrix.get(0,1), 1E-10);
        assertEquals(Double.NaN, matrix.get(1,1), 1E-10);
        assertEquals(Math.round(230000.), matrix.get(2,1), 1E-10);
        assertEquals(Double.NaN, matrix.get(3,1), 1E-10);
    }

    @Test
    public void testFormulaSignumNullable() {
        System.out.println("signum null");
        Formula formula = Formula.rhs(signum("salary"));
        assertEquals(" ~ signum(salary)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.shape(1));
        assertEquals(Math.signum(10000.), output.get(0,0));
        assertNull(output.get(1, 0));
        assertEquals(Math.signum(230000.), output.get(2,0));
        assertNull(output.get(3, 0));
    }

    @Test
    public void testFormulaAddCst() {
        System.out.println("add cst");
        Formula formula = Formula.rhs(dot(), add("age", val(10)));
        assertEquals(" ~ . + (age + 10)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(48, output.get(0,5));
        assertEquals(33, output.get(1,5));
        assertEquals(58, output.get(2,5));
        assertEquals(23, output.get(3,5));
    }

    @Test
    public void testFormulaAddNullable() {
        System.out.println("add nullable");
        Formula formula = Formula.rhs(dot(), add("salary", "age"));
        assertEquals(" ~ . + (salary + age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(10038., output.get(0,5));
        assertNull(output.get(1, 5));
        assertEquals(230048., output.get(2,5));
        assertNull(output.get(3, 5));
    }

    @Test
    public void testFormulaSubCst() {
        System.out.println("sub cst");
        Formula formula = Formula.rhs(dot(), sub("age", val(10)));
        assertEquals(" ~ . + (age - 10)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(28, output.get(0,5));
        assertEquals(13, output.get(1,5));
        assertEquals(38, output.get(2,5));
        assertEquals( 3, output.get(3,5));
    }

    @Test
    public void testFormulaSubNullable() {
        System.out.println("sub nullable");
        Formula formula = Formula.rhs(dot(), sub("salary", "age"));
        assertEquals(" ~ . + (salary - age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(10000.-38, output.get(0,5));
        assertNull(output.get(1, 5));
        assertEquals(230000.-48, output.get(2,5));
        assertNull(output.get(3, 5));
    }

    @Test
    public void testFormulaMulCst() {
        System.out.println("mul cst");
        Formula formula = Formula.rhs(dot(), mul("age", val(10)));
        assertEquals(" ~ . + (age * 10)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(380, output.get(0,5));
        assertEquals(230, output.get(1,5));
        assertEquals(480, output.get(2,5));
        assertEquals(130, output.get(3,5));
    }

    @Test
    public void testFormulaMulNullable() {
        System.out.println("mul nullable");
        Formula formula = Formula.rhs(dot(), mul("salary", "age"));
        assertEquals(" ~ . + (salary * age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(10000.*38, output.get(0,5));
        assertNull(output.get(1, 5));
        assertEquals(230000.*48, output.get(2,5));
        assertNull(output.get(3, 5));
    }

    @Test
    public void testFormulaDivCst() {
        System.out.println("div cst");
        Formula formula = Formula.rhs(dot(), div("age", val(10)));
        assertEquals(" ~ . + (age / 10)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(3, output.get(0,5));
        assertEquals(2, output.get(1,5));
        assertEquals(4, output.get(2,5));
        assertEquals(1, output.get(3,5));
    }

    @Test
    public void testFormulaDivNullable() {
        System.out.println("div nullable");
        Formula formula = Formula.rhs(dot(), div("salary", "age"));
        assertEquals(" ~ . + (salary / age)", formula.toString());

        DataFrame output = formula.frame(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.shape(1));
        assertEquals(10000./38, output.get(0,5));
        assertNull(output.get(1, 5));
        assertEquals(230000./48, output.get(2,5));
        assertNull(output.get(3, 5));
    }

    @Test
    public void testWeatherInteraction() {
        System.out.println("Weather interaction");

        Formula formula = Formula.rhs(interact("outlook", "temperature", "humidity"));
        assertEquals(" ~ outlook:temperature:humidity", formula.toString());

        DataFrame output = formula.frame(weather);
        System.out.println(output);

        StructType schema = new StructType(
                new StructField(
                        "outlook:temperature:humidity",
                        DataTypes.IntType,
                        new NominalScale(
                                "sunny:hot:high", "sunny:hot:normal", "sunny:mild:high", "sunny:mild:normal",
                                "sunny:cool:high", "sunny:cool:normal", "overcast:hot:high", "overcast:hot:normal",
                                "overcast:mild:high", "overcast:mild:normal", "overcast:cool:high", "overcast:cool:normal",
                                "rainy:hot:high", "rainy:hot:normal", "rainy:mild:high", "rainy:mild:normal",
                                "rainy:cool:high", "rainy:cool:normal")
                )
        );
        assertEquals(schema, output.schema());

        assertEquals("sunny:hot:high", output.getString(0, 0));
        assertEquals("sunny:hot:high", output.getString(1, 0));
        assertEquals("overcast:hot:high", output.getString(2, 0));
        assertEquals("rainy:mild:high", output.getString(3, 0));
        assertEquals("rainy:cool:normal", output.getString(4, 0));
        assertEquals("rainy:cool:normal", output.getString(5, 0));
        assertEquals("overcast:cool:normal", output.getString(6, 0));
        assertEquals("sunny:mild:high", output.getString(7, 0));
        assertEquals("sunny:cool:normal", output.getString(8, 0));
        assertEquals("rainy:mild:normal", output.getString(9, 0));
        assertEquals("sunny:mild:normal", output.getString(10, 0));
        assertEquals("overcast:mild:high", output.getString(11, 0));
        assertEquals("overcast:hot:normal", output.getString(12, 0));
        assertEquals("rainy:mild:high", output.getString(13, 0));
    }

    @Test
    public void testWeatherCrossing() {
        System.out.println("Weather crossing");

        Formula formula = Formula.rhs(cross(2, "outlook", "temperature", "humidity"));
        assertEquals(" ~ (outlook x temperature x humidity)^2", formula.toString());

        DataFrame output = formula.frame(weather);
        System.out.println(output);

        StructType schema = new StructType(
                new StructField("outlook", DataTypes.ByteType, new NominalScale("sunny", "overcast", "rainy")),
                new StructField("temperature", DataTypes.ByteType, new NominalScale("hot", "mild", "cool")),
                new StructField("humidity", DataTypes.ByteType, new NominalScale("high", "normal")),
                new StructField("outlook:temperature", DataTypes.IntType, new NominalScale("sunny:hot", "sunny:mild", "sunny:cool", "overcast:hot", "overcast:mild", "overcast:cool", "rainy:hot", "rainy:mild", "rainy:cool")),
                new StructField("outlook:humidity", DataTypes.IntType, new NominalScale("sunny:high", "sunny:normal", "overcast:high", "overcast:normal", "rainy:high", "rainy:normal")),
                new StructField("temperature:humidity", DataTypes.IntType, new NominalScale("hot:high", "hot:normal", "mild:high", "mild:normal", "cool:high", "cool:normal"))
        );
        assertEquals(schema, output.schema());

        assertEquals("sunny:hot", output.getString(0, 3));
        assertEquals("sunny:hot", output.getString(1, 3));
        assertEquals("overcast:hot", output.getString(2, 3));
        assertEquals("rainy:mild", output.getString(3, 3));
        assertEquals("rainy:cool", output.getString(4, 3));
        assertEquals("rainy:cool", output.getString(5, 3));
        assertEquals("overcast:cool", output.getString(6, 3));
        assertEquals("sunny:mild", output.getString(7, 3));
        assertEquals("sunny:cool", output.getString(8, 3));
        assertEquals("rainy:mild", output.getString(9, 3));
        assertEquals("sunny:mild", output.getString(10, 3));
        assertEquals("overcast:mild", output.getString(11, 3));
        assertEquals("overcast:hot", output.getString(12, 3));
        assertEquals("rainy:mild", output.getString(13, 3));
    }
}