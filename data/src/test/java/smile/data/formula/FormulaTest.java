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
package smile.data.formula;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import smile.data.DataFrame;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.matrix.DenseMatrix;
import static smile.data.formula.Terms.*;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FormulaTest {

    enum Gender {
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

    public FormulaTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", Gender.Male, LocalDate.of(1980, 10, 1), 38, 10000.));
        persons.add(new Person("Bob", Gender.Male, LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", Gender.Female, LocalDate.of(1970, 3, 1), 48, 230000.));
        persons.add(new Person("Amy", Gender.Female, LocalDate.of(2005, 12, 10), 13, null));

        df = DataFrame.of(persons, Person.class);
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

    @Test
    public void testToString() {
        System.out.println("toString");
        Formula formula = Formula.lhs("salary");
        assertEquals("salary ~ .", formula.toString());

        formula = Formula.rhs($("salary"));
        assertEquals(" ~ salary", formula.toString());

        formula = Formula.of("salary", all(), cross("a", "b", "c") , delete("d"));
        assertEquals("salary ~ . + (a x b x c) - d", formula.toString());
    }

    @Test
    public void testAll() {
        System.out.println("all");
        Formula formula = Formula.of("salary", all(), log("age"), onehot("gender"));
        assertEquals("salary ~ . + log(age) + one-hot(gender)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);

        StructType schema = DataTypes.struct(
                new StructField("salary", DataTypes.DoubleObjectType),
                new StructField("age", DataTypes.IntegerType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("gender", DataTypes.ByteType, new NominalScale("Male", "Female")),
                new StructField("name", DataTypes.StringType),
                new StructField("log(age)", DataTypes.DoubleType),
                new StructField("gender_Male", DataTypes.ByteType),
                new StructField("gender_Female", DataTypes.ByteType)
        );
        assertEquals(schema, formula.schema());
    }

    @Test
    public void testFormula() {
        System.out.println("formula");
        Formula formula = Formula.of("salary", $("age"));
        assertEquals("salary ~ age", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(2, output.ncols());

        smile.data.type.StructType schema = DataTypes.struct(
                new StructField("salary", DataTypes.object(Double.class)),
                new StructField("age", DataTypes.IntegerType)
        );
        assertEquals(schema, output.schema());

        DataFrame x = formula.x(df);
        System.out.println(x);
        assertEquals(df.size(), x.size());
        assertEquals(1, x.ncols());

        smile.data.type.StructType xschema = DataTypes.struct(
                new StructField("age", DataTypes.IntegerType)
        );
        assertEquals(xschema, x.schema());

        assertEquals(10000.0, formula.y(df.get(0)), 1E-7);
        assertEquals(Double.NaN, formula.y(df.get(1)), 1E-7);

        DenseMatrix matrix = formula.matrix(df);
        assertEquals(df.size(), matrix.nrows());
        assertEquals(1, matrix.ncols());
    }

    @Test
    public void testInteraction() {
        System.out.println("interaction");

        Formula formula = Formula.rhs(interact("water", "sowing_density", "wind"));
        StructType inputSchema = DataTypes.struct(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong"))
        );
        assertEquals(" ~ water:sowing_density:wind", formula.toString());

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = DataTypes.struct(
                new StructField("water_dry-sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high-wind_strong", DataTypes.ByteType)
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testCrossing() {
        System.out.println("crossing");

        Formula formula = Formula.rhs(cross(2, "water", "sowing_density", "wind"));
        StructType inputSchema = DataTypes.struct(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong"))
        );
        assertEquals(" ~ (water x sowing_density x wind)^2", formula.toString());

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = DataTypes.struct(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong")),
                new StructField("sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("sowing_density_high-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_low", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high", DataTypes.ByteType)
        );
        assertEquals(schema, outputSchema);

        formula = Formula.rhs(cross("water", "sowing_density", "wind"));
        assertEquals(" ~ (water x sowing_density x wind)", formula.toString());

        outputSchema = formula.bind(inputSchema);
        schema = DataTypes.struct(
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("wind", DataTypes.ByteType, new NominalScale("weak", "strong")),
                new StructField("sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("sowing_density_high-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_low", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high-wind_weak", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low-wind_strong", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high-wind_strong", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high-wind_strong", DataTypes.ByteType)
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testBind() {
        System.out.println("bind");

        Formula formula = Formula.of("revenue", all(), cross("water", "sowing_density") , mul("humidity", "wind"), delete("wind"));
        StructType inputSchema = DataTypes.struct(
                new StructField("revenue", DataTypes.DoubleType, Measure.Currency),
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("humidity", DataTypes.FloatType, Measure.Percent),
                new StructField("wind", DataTypes.FloatType)
        );
        assertEquals("revenue ~ . + (water x sowing_density) + (humidity * wind) - wind", formula.toString());

        StructType outputSchema = formula.bind(inputSchema);
        StructType schema = DataTypes.struct(
                new StructField("revenue", DataTypes.DoubleType, Measure.Currency),
                new StructField("humidity", DataTypes.FloatType, Measure.Percent),
                new StructField("water", DataTypes.ByteType, new NominalScale("dry", "wet")),
                new StructField("sowing_density", DataTypes.ByteType, new NominalScale("low", "high")),
                new StructField("water_dry-sowing_density_low", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_low", DataTypes.ByteType),
                new StructField("water_dry-sowing_density_high", DataTypes.ByteType),
                new StructField("water_wet-sowing_density_high", DataTypes.ByteType),
                new StructField("humidity * wind", DataTypes.FloatType)
        );
        assertEquals(schema, outputSchema);
    }

    @Test
    public void testFormulaOneHot() {
        System.out.println("one-hot");
        Formula formula = Formula.rhs(onehot("gender"));
        assertEquals(" ~ one-hot(gender)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(2, output.ncols());
        assertEquals(1, output.getByte(0,0));
        assertEquals(0, output.getByte(0,1));
        assertEquals(1, output.getByte(1,0));
        assertEquals(0, output.getByte(1,1));
        assertEquals(0, output.getByte(2,0));
        assertEquals(1, output.getByte(2,1));
        assertEquals(0, output.getByte(3,0));
        assertEquals(1, output.getByte(3,1));
    }

    @Test
    public void testFormulaDate() {
        System.out.println("date");
        Formula formula = Formula.rhs(date("birthday", DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH, DateFeature.DAY_OF_WEEK));
        assertEquals(" ~ birthday[YEAR, MONTH, DAY_OF_MONTH, DAY_OF_WEEK]", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output.schema());
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.abs(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.abs(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaExp() {
        System.out.println("exp");
        Formula formula = Formula.rhs(exp("age"));
        assertEquals(" ~ exp(age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.exp(1), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.exp(23), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaLog() {
        System.out.println("log");
        Formula formula = Formula.rhs(log("age"));
        assertEquals(" ~ log(age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.log(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaLog10() {
        System.out.println("log10");
        Formula formula = Formula.rhs(log10("age"));
        assertEquals(" ~ log10(age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log10(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.log10(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaSqrt() {
        System.out.println("sqrt");
        Formula formula = Formula.rhs(sqrt("age"));
        assertEquals(" ~ sqrt(age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
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

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.sqrt(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.sqrt(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaCeilNullable() {
        System.out.println("ceil null");
        Formula formula = Formula.rhs(ceil("salary"));
        assertEquals(" ~ ceil(salary)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.ceil(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.ceil(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaFloorNullable() {
        System.out.println("floor null");
        Formula formula = Formula.rhs(floor("salary"));
        assertEquals(" ~ floor(salary)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.floor(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.floor(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaRoundNullable() {
        System.out.println("round null");
        Formula formula = Formula.rhs(round("salary"));
        assertEquals(" ~ round(salary)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.round(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.round(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));

        DenseMatrix matrix = formula.matrix(df);
        System.out.println(matrix);
        System.out.println(matrix.nrows());
        System.out.println(matrix.ncols());
        assertEquals(df.size(), matrix.nrows());
        assertEquals(1, matrix.ncols());
        assertEquals(Math.round(10000.), matrix.get(0,0), 1E-10);
        assertEquals(Double.NaN, matrix.get(1,0), 1E-10);
        assertEquals(Math.round(230000.), matrix.get(2,0), 1E-10);
        assertEquals(Double.NaN, matrix.get(3,0), 1E-10);

        DenseMatrix matrix1 = formula.matrix(df, true);
        System.out.println(matrix1);
        assertEquals(df.size(), matrix1.nrows());
        assertEquals(2, matrix1.ncols());
        assertEquals(Math.round(10000.), matrix1.get(0,0), 1E-10);
        assertEquals(Double.NaN, matrix1.get(1,0), 1E-10);
        assertEquals(Math.round(230000.), matrix.get(2,0), 1E-10);
        assertEquals(Double.NaN, matrix1.get(3,0), 1E-10);

        assertEquals(1.0, matrix1.get(0,1), 1E-10);
        assertEquals(1.0, matrix1.get(1,1), 1E-10);
        assertEquals(1.0, matrix1.get(2,1), 1E-10);
        assertEquals(1.0, matrix1.get(3,1), 1E-10);
    }

    @Test
    public void testFormulaSignumNullable() {
        System.out.println("signum null");
        Formula formula = Formula.rhs(signum("salary"));
        assertEquals(" ~ signum(salary)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.signum(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.signum(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    @Test
    public void testFormulaAddCst() {
        System.out.println("add cst");
        Formula formula = Formula.rhs(all(), add("age", val(10)));
        assertEquals(" ~ . + (age + 10)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(48, output.get(0,5));
        assertEquals(33, output.get(1,5));
        assertEquals(58, output.get(2,5));
        assertEquals(23, output.get(3,5));
    }

    @Test
    public void testFormulaAddNullable() {
        System.out.println("add nullable");
        Formula formula = Formula.rhs(all(), add("salary", "age"));
        assertEquals(" ~ . + (salary + age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(10038., output.get(0,5));
        assertEquals(null, output.get(1,5));
        assertEquals(230048., output.get(2,5));
        assertEquals(null, output.get(3,5));
    }

    @Test
    public void testFormulaSubCst() {
        System.out.println("sub cst");
        Formula formula = Formula.rhs(all(), sub("age", val(10)));
        assertEquals(" ~ . + (age - 10)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(28, output.get(0,5));
        assertEquals(13, output.get(1,5));
        assertEquals(38, output.get(2,5));
        assertEquals( 3, output.get(3,5));
    }

    @Test
    public void testFormulaSubNullable() {
        System.out.println("sub nullable");
        Formula formula = Formula.rhs(all(), sub("salary", "age"));
        assertEquals(" ~ . + (salary - age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(10000.-38, output.get(0,5));
        assertEquals(null, output.get(1,5));
        assertEquals(230000.-48, output.get(2,5));
        assertEquals(null, output.get(3,5));
    }

    @Test
    public void testFormulaMulCst() {
        System.out.println("mul cst");
        Formula formula = Formula.rhs(all(), mul("age", val(10)));
        assertEquals(" ~ . + (age * 10)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(380, output.get(0,5));
        assertEquals(230, output.get(1,5));
        assertEquals(480, output.get(2,5));
        assertEquals(130, output.get(3,5));
    }

    @Test
    public void testFormulaMulNullable() {
        System.out.println("mul nullable");
        Formula formula = Formula.rhs(all(), mul("salary", "age"));
        assertEquals(" ~ . + (salary * age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(10000.*38, output.get(0,5));
        assertEquals(null, output.get(1,5));
        assertEquals(230000.*48, output.get(2,5));
        assertEquals(null, output.get(3,5));
    }

    @Test
    public void testFormulaDivCst() {
        System.out.println("div cst");
        Formula formula = Formula.rhs(all(), div("age", val(10)));
        assertEquals(" ~ . + (age / 10)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(3, output.get(0,5));
        assertEquals(2, output.get(1,5));
        assertEquals(4, output.get(2,5));
        assertEquals(1, output.get(3,5));
    }

    @Test
    public void testFormulaDivNullable() {
        System.out.println("div nullable");
        Formula formula = Formula.rhs(all(), div("salary", "age"));
        assertEquals(" ~ . + (salary / age)", formula.toString());

        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(6, output.ncols());
        assertEquals(10000./38, output.get(0,5));
        assertEquals(null, output.get(1,5));
        assertEquals(230000./48, output.get(2,5));
        assertEquals(null, output.get(3,5));
    }
}