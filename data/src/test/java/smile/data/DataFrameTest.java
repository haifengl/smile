/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import smile.data.formula.Formula;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import static smile.data.formula.Formula.*;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class DataFrameTest {

    static class Person {
        String name;
        char gender;
        LocalDate birthday;
        int age;
        Integer salary;
        Person(String name, char gender, LocalDate birthday, int age, Integer salary) {
            this.name = name;
            this.gender = gender;
            this.birthday = birthday;
            this.age = age;
            this.salary = salary;
        }

        public String getName() { return name; }
        public char getGender() { return gender; }
        public LocalDate getBirthday() { return birthday; }
        public int getAge() { return age; }
        public Integer getSalary() { return salary; }
    }

    DataFrame df;

    public DataFrameTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", 'M', LocalDate.of(1980, 10, 1), 38, 10000));
        persons.add(new Person("Bob", 'M', LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", 'F', LocalDate.of(1970, 3, 1), 48, 230000));
        persons.add(new Person("Amy", 'F', LocalDate.of(2005, 12, 10), 13, null));

        df = DataFrame.of(persons, Person.class);
        System.out.println(df);
        System.out.println(Arrays.toString(df.names()));
        System.out.println(Arrays.toString(df.types()));
        System.out.println(df.structure());
        System.out.println(df.schema());
        System.out.println(df.schema().name());
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

    /**
     * Test of nrows method, of class DataFrame.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(4, df.nrows());
    }

    /**
     * Test of ncols method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(5, df.ncols());
    }

    /**
     * Test of size method, of class DataFrame.
     */
    @Test
    public void testNames() {
        System.out.println("names");
        String[] names = {"age", "birthday", "gender", "name", "salary"};
        assertTrue(Arrays.equals(names, df.names()));
    }

    /**
     * Test of size method, of class DataFrame.
     */
    @Test
    public void testTypes() {
        System.out.println("names");
        DataType[] types = {DataTypes.IntegerType, DataTypes.DateType, DataTypes.CharType, DataTypes.StringType, DataTypes.object(Integer.class)};
        assertTrue(Arrays.equals(types, df.types()));
    }

    /**
     * Test of get method, of class DataFrame.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(38, df.get(0).getInt(0));
        assertEquals("Alex", df.get(0).getString(3));
        assertEquals(10000, df.get(0).get(4));
        assertEquals(13, df.get(3).getInt(0));
        assertEquals("Amy", df.get(3).getString(3));
        assertEquals(null, df.get(3).get(4));

        assertEquals(38, df.get(0,0));
        assertEquals("Alex", df.get(0,3));
        assertEquals(10000, df.get(0,4));
        assertEquals(13, df.get(3,0));
        assertEquals("Amy", df.get(3,3));
        assertEquals(null, df.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaAdd() {
        System.out.println("Add");
        Formula formula = new Formula(all(), add("age", cst(10)));
        DataFrame output = formula.apply(df);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(48, output.get(0,0));
        assertEquals(33, output.get(1,0));
        assertEquals(58, output.get(2,0));
        assertEquals(23, output.get(3,0));
    }
}