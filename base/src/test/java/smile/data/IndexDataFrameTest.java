/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import smile.data.measure.NominalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.matrix.Matrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class IndexDataFrameTest {

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

    public IndexDataFrameTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", Gender.Male, LocalDate.of(1980, 10, 1), 38, 10000.));
        persons.add(new Person("Bob", Gender.Male, LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", Gender.Female, LocalDate.of(1970, 3, 1), 48, 230000.));
        persons.add(new Person("Amy", Gender.Female, LocalDate.of(2005, 12, 10), 13, null));

        df = DataFrame.of(persons, Person.class);
        df = df.of(2,1,3,2);
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

    /**
     * Test of nrow method, of class DataFrame.
     */
    @Test
    public void testNrows() {
        System.out.println("nrow");
        assertEquals(4, df.nrow());
    }

    /**
     * Test of ncol method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncol");
        assertEquals(5, df.ncol());
    }

    /**
     * Test of schema method, of class DataFrame.
     */
    @Test
    public void testSchema() {
        System.out.println("schema");
        System.out.println(df.schema());
        System.out.println(df.structure());
        System.out.println(df);
        smile.data.type.StructType schema = DataTypes.struct(
                new StructField("age", DataTypes.IntegerType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("gender", DataTypes.ByteType, new NominalScale("Male", "Female")),
                new StructField("name", DataTypes.StringType),
                new StructField("salary", DataTypes.object(Double.class))
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
        System.out.println("names");
        DataType[] types = {DataTypes.IntegerType, DataTypes.DateType, DataTypes.ByteType, DataTypes.StringType, DataTypes.object(Double.class)};
        assertArrayEquals(types, df.types());
    }

    /**
     * Test of get method, of class DataFrame.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        System.out.println(df);
        System.out.println(df.get(0));
        System.out.println(df.get(1));
        assertEquals(48, df.getInt(0, 0));
        assertEquals("Jane", df.getString(0, 3));
        assertEquals(230000., df.get(0, 4));
        assertEquals(23, df.getInt(1, 0));
        assertEquals("Bob", df.getString(1, 3));
        assertNull(df.get(1, 4));
        assertEquals(13, df.getInt(2, 0));
        assertEquals("Amy", df.getString(2, 3));
        assertNull(df.get(2, 4));
        assertEquals(48, df.getInt(3, 0));
        assertEquals("Jane", df.getString(3, 3));
        assertEquals(230000., df.get(3, 4));
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToMatrix() {
        System.out.println("toMatrix");
        Matrix output = df.select("name", "age", "salary", "gender").toMatrix(false, CategoricalEncoder.ONE_HOT, "name");
        System.out.println(output);
        assertEquals(4, output.nrow());
        assertEquals(4, output.ncol());
        assertEquals(48., output.get(0, 0), 1E-10);
        assertEquals(23., output.get(1, 0), 1E-10);
        assertEquals(13., output.get(2, 0), 1E-10);
        assertEquals(48., output.get(3, 0), 1E-10);
        assertEquals(230000., output.get(0, 1), 1E-10);
        assertTrue(Double.isNaN(output.get(1, 1)));
        assertTrue(Double.isNaN(output.get(2, 1)));
        assertEquals(230000., output.get(3, 1), 1E-10);
        assertEquals(0, output.get(0, 2), 1E-10);
        assertEquals(1, output.get(1, 2), 1E-10);
        assertEquals(0, output.get(2, 2), 1E-10);
        assertEquals(0, output.get(3, 2), 1E-10);
        assertEquals(1, output.get(0, 3), 1E-10);
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
        double[][] output = df.select("age", "salary", "gender").toArray(false, CategoricalEncoder.ONE_HOT);
        assertEquals(4, output.length);
        assertEquals(4, output[0].length);
        assertEquals(48., output[0][0], 1E-10);
        assertEquals(23., output[1][0], 1E-10);
        assertEquals(13., output[2][0], 1E-10);
        assertEquals(48., output[3][0], 1E-10);
        assertEquals(230000., output[0][1], 1E-10);
        assertTrue(Double.isNaN(output[1][1]));
        assertTrue(Double.isNaN(output[2][1]));
        assertEquals(230000., output[3][1], 1E-10);
        assertEquals(0, output[0][2], 1E-10);
        assertEquals(1, output[1][2], 1E-10);
        assertEquals(0, output[2][2], 1E-10);
        assertEquals(0, output[3][2], 1E-10);
        assertEquals(1, output[0][3], 1E-10);
        assertEquals(0, output[1][3], 1E-10);
        assertEquals(1, output[2][3], 1E-10);
        assertEquals(1, output[3][3], 1E-10);
    }
}