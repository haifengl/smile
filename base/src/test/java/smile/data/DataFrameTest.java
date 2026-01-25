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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import smile.data.measure.NominalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.StringVector;
import smile.math.MathEx;
import smile.tensor.Matrix;
import org.junit.jupiter.api.*;
import smile.util.Dates;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DataFrameTest {

    enum Gender {
        Male,
        Female
    }

    static class Person {
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

    public DataFrameTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", Gender.Male, LocalDate.of(1980, 10, 1), 38, 10000.));
        persons.add(new Person("Bob", Gender.Male, LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", Gender.Female, LocalDate.of(1970, 3, 1), 48, 230000.));
        persons.add(new Person("Amy", Gender.Female, LocalDate.of(2005, 12, 10), 13, null));

        df = DataFrame.of(Person.class, persons);
        System.out.println(df.schema());
        System.out.println(df);
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
    public void testSize() {
        System.out.println("size");
        assertEquals(4, df.size());
    }

    @Test
    public void testColumnLength() {
        System.out.println("column length");
        assertEquals(5, df.columns().size());
    }

    /**
     * Test of schema method, of class DataFrame.
     */
    @Test
    public void testSchema() {
        System.out.println("schema");
        System.out.println(df.schema());
        System.out.println(df.describe());
        System.out.println(df);
        smile.data.type.StructType schema = new StructType(
                new StructField("age", DataTypes.IntType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("gender", DataTypes.ByteType, new NominalScale("Male", "Female")),
                new StructField("name", DataTypes.StringType),
                new StructField("salary", DataTypes.NullableDoubleType)
        );
        assertEquals(schema, df.schema());
    }

    /**
     * Test of names method, of class DataFrame.
     */
    @Test
    public void testNames() {
        System.out.println("names");
        String[] names = {"age", "birthday", "gender", "name", "salary"};
        assertArrayEquals(names, df.names());
    }

    /**
     * Test of types method, of class DataFrame.
     */
    @Test
    public void testTypes() {
        System.out.println("dtypes");
        DataType[] dtypes = {DataTypes.IntType, DataTypes.DateType, DataTypes.ByteType, DataTypes.StringType, DataTypes.NullableDoubleType};
        assertArrayEquals(dtypes, df.dtypes());
    }

    @Test
    public void testGet() {
        System.out.println("get");
        System.out.println(df.get(0));
        System.out.println(df.get(1));
        assertEquals(38, df.get(0).getInt(0));
        assertEquals("Alex", df.get(0).getString(3));
        assertEquals(10000., df.get(0).get(4));
        assertEquals(13, df.get(3).getInt(0));
        assertEquals("Amy", df.get(3).getString(3));
        assertNull(df.get(3).get(4));

        assertEquals(38, df.get(0,0));
        assertEquals("Alex", df.get(0,3));
        assertEquals(10000., df.get(0,4));
        assertEquals(13, df.get(3,0));
        assertEquals("Amy", df.get(3,3));
        assertNull(df.get(3, 4));
    }

    @Test
    public void testSet() {
        System.out.println("set");
        StringVector edu = new StringVector("Education", new String[]{"MS", "BS", "Ph.D", "Middle School"});
        DataFrame two = df.set("Education", edu);
        assertEquals(38, two.get(0, 0));
        assertEquals("Alex", two.getString(0, 3));
        assertEquals(10000., two.get(0, 4));
        assertEquals("MS", two.get(0, 5));
        assertEquals(13, two.get(3, 0));
        assertEquals("Amy", two.getString(3, 3));
        assertNull(two.get(3, 4));
        assertEquals("Middle School", two.get(3, 5));
    }

    @Test
    public void testAdd() {
        System.out.println("add");
        StringVector edu = new StringVector("Education", new String[]{"MS", "BS", "Ph.D", "Middle School"});
        DataFrame two = df.add(edu);
        assertEquals(38, two.get(0, 0));
        assertEquals("Alex", two.getString(0, 3));
        assertEquals(10000., two.get(0, 4));
        assertEquals("MS", two.get(0, 5));
        assertEquals(13, two.get(3, 0));
        assertEquals("Amy", two.getString(3, 3));
        assertNull(two.get(3, 4));
        assertEquals("Middle School", two.get(3, 5));
    }

    @Test
    public void testDrop() {
        System.out.println("drop");
        DataFrame two = df.drop("salary");
        assertEquals(4, two.nrow());
        assertEquals(4, two.ncol());
        assertEquals(38, two.get(0, 0));
        assertEquals("Alex", two.getString(0, 3));
        assertEquals(13, two.get(3, 0));
        assertEquals("Amy", two.getString(3, 3));
    }

    @Test
    public void testSelect() {
        System.out.println("select");
        StringVector edu = new StringVector("Education", new String[]{"MS", "BS", "Ph.D", "Middle School"});
        DataFrame two = df.select("name", "salary");
        assertEquals(4, two.nrow());
        assertEquals(2, two.ncol());
        assertEquals("Alex", two.getString(0, 0));
        assertEquals(10000., two.get(0, 1));
        assertEquals("Amy", two.getString(3, 0));
        assertNull(two.get(3, 1));
    }

    @Test
    public void testJoin() {
        System.out.println("join");
        var dates = Dates.range(LocalDate.of(2025,2,1), 6);
        var df1 = DataFrame.of(MathEx.randn(6, 4)).setIndex(dates);
        var df2 = DataFrame.of(MathEx.randn(6, 4)).setIndex(dates);
        var df = df1.join(df2);

        assertEquals(6, df.nrow());
        assertEquals(8, df.ncol());
        assertEquals(df1.get(0, 0), df.get(0, 0));
        assertEquals(df2.get(0, 0), df.get(0, 4));
        assertEquals(df1.get(3, 0), df.get(3, 0));
        assertEquals(df2.get(3, 0), df.get(3, 4));
        assertEquals(df1.get(5, 0), df.get(5, 0));
        assertEquals(df2.get(5, 0), df.get(5, 4));
    }

    @Test
    public void testMerge() {
        System.out.println("merge");
        DataFrame two = df.merge(df);
        assertEquals(4, two.nrow());
        assertEquals(10, two.ncol());
        assertEquals(38, two.get(0, 0));
        assertEquals("Alex", two.getString(0, 3));
        assertEquals(10000., two.get(0, 4));
        assertEquals(38, two.get(0, 5));
        assertEquals("Alex", two.getString(0, 8));
        assertEquals(10000., two.get(0, 9));
        assertEquals(13, two.get(3, 0));
        assertEquals("Amy", two.getString(3, 3));
        assertNull(two.get(3, 4));
        assertEquals(13, two.get(3, 5));
        assertEquals("Amy", two.getString(3, 8));
        assertNull(two.get(3, 9));
    }

    @Test
    public void testConcat() {
        System.out.println("concat");
        DataFrame two = df.concat(df);
        assertEquals(2*df.size(), two.size());
        assertEquals(df.columns().size(), two.columns().size());

        assertEquals(38, two.get(0,0));
        assertEquals("Alex", two.get(0, 3));
        assertEquals(10000., two.get(0).get(4));
        assertEquals(13, two.get(3).getInt(0));
        assertEquals("Amy", two.get(3).getString(3));
        assertNull(two.get(3).get(4));

        assertEquals(38, two.get(4, 0));
        assertEquals("Alex", two.getString(4, 3));
        assertEquals(10000., two.get(4, 4));
        assertEquals(13, two.get(7, 0));
        assertEquals("Amy", two.getString(7, 3));
        assertNull(two.get(7, 4));
    }

    @Test
    public void testFactorize() {
        System.out.println("factorize");
        var two = df.factorize();
        System.out.println(two.schema());
        System.out.println(two);
        assertTrue(two.schema().field("name").dtype().isIntegral());
        assertTrue(two.schema().field("name").measure() instanceof NominalScale);
        assertEquals(38, two.get(0,0));
        assertEquals(0, two.get(0, 3));
        assertEquals("Alex", two.getString(0, 3));
        assertEquals(10000., two.get(0).get(4));
        assertEquals(13, two.get(3).getInt(0));
        assertEquals(1, two.get(3, 3));
        assertEquals("Amy", two.get(3).getString(3));
        assertNull(two.get(3).get(4));
    }

    @Test
    public void testDescribe() {
        System.out.println("describe");
        DataFrame output = df.describe();
        System.out.println(output);
        System.out.println(output.schema());
        assertEquals(5, output.size());
        assertEquals(12, output.columns().size());
        assertEquals("age", output.get(0,0));
        assertEquals(4, output.get(0,3));
        assertEquals(13, output.get(0,4));
        assertEquals(30.5, output.get(0,5));
        assertEquals(48.0, output.get(0,11));
        assertEquals("salary", output.get(4,0));
        assertEquals(2, output.get(4,3));
        assertEquals(10000., output.get(4,7));
        assertEquals(120000., output.get(4,5));
        assertEquals(230000., output.get(4,11));
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToMatrix() {
        System.out.println("toMatrix");
        Matrix output = df.select("name", "age", "salary", "gender").toMatrix(true, CategoricalEncoder.DUMMY, "name");
        System.out.println(output);
        assertEquals(4, output.nrow());
        assertEquals(4, output.ncol());
        assertEquals(38., output.get(0, 1), 1E-10);
        assertEquals(23., output.get(1, 1), 1E-10);
        assertEquals(48., output.get(2, 1), 1E-10);
        assertEquals(13., output.get(3, 1), 1E-10);
        assertEquals(10000., output.get(0, 2), 1E-10);
        assertTrue(Double.isNaN(output.get(1, 2)));
        assertEquals(230000., output.get(2, 2), 1E-10);
        assertTrue(Double.isNaN(output.get(3, 2)));
        assertEquals(0, output.get(0, 3), 1E-10);
        assertEquals(0, output.get(1, 3), 1E-10);
        assertEquals(1, output.get(2, 3), 1E-10);
        assertEquals(1, output.get(3, 3), 1E-10);
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToArray() {
        System.out.println("toArray");
        double[][] output = df.select("age", "salary", "gender").toArray(true, CategoricalEncoder.DUMMY);
        assertEquals(4, output.length);
        assertEquals(4, output[0].length);
        assertEquals(1, output[0][0], 1E-10);
        assertEquals(1, output[1][0], 1E-10);
        assertEquals(1, output[2][0], 1E-10);
        assertEquals(1, output[3][0], 1E-10);
        assertEquals(38., output[0][1], 1E-10);
        assertEquals(23., output[1][1], 1E-10);
        assertEquals(48., output[2][1], 1E-10);
        assertEquals(13., output[3][1], 1E-10);
        assertEquals(10000., output[0][2], 1E-10);
        assertTrue(Double.isNaN(output[1][2]));
        assertEquals(230000., output[2][2], 1E-10);
        assertTrue(Double.isNaN(output[3][2]));
        assertEquals(0, output[0][3], 1E-10);
        assertEquals(0, output[1][3], 1E-10);
        assertEquals(1, output[2][3], 1E-10);
        assertEquals(1, output[3][3], 1E-10);
    }
}
