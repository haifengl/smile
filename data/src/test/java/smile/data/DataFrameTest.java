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
import smile.data.type.StructField;

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
        Double salary;
        Person(String name, char gender, LocalDate birthday, int age, Double salary) {
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
        public Double getSalary() { return salary; }
    }

    DataFrame df;

    public DataFrameTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", 'M', LocalDate.of(1980, 10, 1), 38, 10000.));
        persons.add(new Person("Bob", 'M', LocalDate.of(1995, 3, 4), 23, null));
        persons.add(new Person("Jane", 'F', LocalDate.of(1970, 3, 1), 48, 230000.));
        persons.add(new Person("Amy", 'F', LocalDate.of(2005, 12, 10), 13, null));

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
                new StructField("gender", DataTypes.CharType),
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
        assertTrue(Arrays.equals(names, df.names()));
    }

    /**
     * Test of types method, of class DataFrame.
     */
    @Test
    public void testTypes() {
        System.out.println("names");
        DataType[] types = {DataTypes.IntegerType, DataTypes.DateType, DataTypes.CharType, DataTypes.StringType, DataTypes.object(Double.class)};
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
        assertEquals(10000., df.get(0).get(4));
        assertEquals(13, df.get(3).getInt(0));
        assertEquals("Amy", df.get(3).getString(3));
        assertEquals(null, df.get(3).get(4));

        assertEquals(38, df.get(0,0));
        assertEquals("Alex", df.get(0,3));
        assertEquals(10000., df.get(0,4));
        assertEquals(13, df.get(3,0));
        assertEquals("Amy", df.get(3,3));
        assertEquals(null, df.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaAbs() {
        System.out.println("abs");
        Formula formula = new Formula(abs("age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.abs(38), output.get(0,0));
        assertEquals(Math.abs(23), output.get(1,0));
        assertEquals(Math.abs(48), output.get(2,0));
        assertEquals(Math.abs(13), output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaAbsNullable() {
        System.out.println("abs null");
        Formula formula = new Formula(abs("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.abs(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.abs(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaExp() {
        System.out.println("exp");
        Formula formula = new Formula(exp("age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.exp(38), output.get(0,0));
        assertEquals(Math.exp(23), output.get(1,0));
        assertEquals(Math.exp(48), output.get(2,0));
        assertEquals(Math.exp(13), output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaExpNullable() {
        System.out.println("exp null");
        Formula formula = new Formula(exp("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.exp(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.exp(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }
    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaLog() {
        System.out.println("log");
        Formula formula = new Formula(log("age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log(38), output.get(0,0));
        assertEquals(Math.log(23), output.get(1,0));
        assertEquals(Math.log(48), output.get(2,0));
        assertEquals(Math.log(13), output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaLogNullable() {
        System.out.println("log null");
        Formula formula = new Formula(log("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.log(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaLog10() {
        System.out.println("log10");
        Formula formula = new Formula(log10("age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log10(38), output.get(0,0));
        assertEquals(Math.log10(23), output.get(1,0));
        assertEquals(Math.log10(48), output.get(2,0));
        assertEquals(Math.log10(13), output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaLog10Nullable() {
        System.out.println("log null");
        Formula formula = new Formula(log10("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.log10(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.log10(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaSqrt() {
        System.out.println("sqrt");
        Formula formula = new Formula(sqrt("age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.sqrt(38), output.get(0,0));
        assertEquals(Math.sqrt(23), output.get(1,0));
        assertEquals(Math.sqrt(48), output.get(2,0));
        assertEquals(Math.sqrt(13), output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaSqrtNullable() {
        System.out.println("sqrt null");
        Formula formula = new Formula(sqrt("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.sqrt(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.sqrt(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaCeilNullable() {
        System.out.println("ceil null");
        Formula formula = new Formula(ceil("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.ceil(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.ceil(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaFloorNullable() {
        System.out.println("floor null");
        Formula formula = new Formula(floor("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.floor(10000), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.floor(230000), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaRoundNullable() {
        System.out.println("round null");
        Formula formula = new Formula(round("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.round(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.round(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaSignumNullable() {
        System.out.println("signum null");
        Formula formula = new Formula(signum("salary"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncols());
        assertEquals(Math.signum(10000.), output.get(0,0));
        assertEquals(null, output.get(1,0));
        assertEquals(Math.signum(230000.), output.get(2,0));
        assertEquals(null, output.get(3,0));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaAddCst() {
        System.out.println("add cst");
        Formula formula = new Formula(all(), add("age", val(10)));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(5, output.ncols());
        assertEquals(48, output.get(0,4));
        assertEquals(33, output.get(1,4));
        assertEquals(58, output.get(2,4));
        assertEquals(23, output.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaAddNullable() {
        System.out.println("add nullable");
        Formula formula = new Formula(all(), add("salary", "age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.ncols());
        assertEquals(10038., output.get(0,3));
        assertEquals(null, output.get(1,3));
        assertEquals(230048., output.get(2,3));
        assertEquals(null, output.get(3,3));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaSubCst() {
        System.out.println("sub cst");
        Formula formula = new Formula(all(), sub("age", val(10)));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(5, output.ncols());
        assertEquals(28, output.get(0,4));
        assertEquals(13, output.get(1,4));
        assertEquals(38, output.get(2,4));
        assertEquals( 3, output.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaSubNullable() {
        System.out.println("sub nullable");
        Formula formula = new Formula(all(), sub("salary", "age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.ncols());
        assertEquals(10000.-38, output.get(0,3));
        assertEquals(null, output.get(1,3));
        assertEquals(230000.-48, output.get(2,3));
        assertEquals(null, output.get(3,3));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaMulCst() {
        System.out.println("mul cst");
        Formula formula = new Formula(all(), mul("age", val(10)));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(5, output.ncols());
        assertEquals(380, output.get(0,4));
        assertEquals(230, output.get(1,4));
        assertEquals(480, output.get(2,4));
        assertEquals(130, output.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaMulNullable() {
        System.out.println("mul nullable");
        Formula formula = new Formula(all(), mul("salary", "age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.ncols());
        assertEquals(10000.*38, output.get(0,3));
        assertEquals(null, output.get(1,3));
        assertEquals(230000.*48, output.get(2,3));
        assertEquals(null, output.get(3,3));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaDivCst() {
        System.out.println("div cst");
        Formula formula = new Formula(all(), div("age", val(10)));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(5, output.ncols());
        assertEquals(3, output.get(0,4));
        assertEquals(2, output.get(1,4));
        assertEquals(4, output.get(2,4));
        assertEquals(1, output.get(3,4));
    }

    /**
     * Test of apply method, of class Formula.
     */
    @Test
    public void testFormulaDivNullable() {
        System.out.println("div nullable");
        Formula formula = new Formula(all(), div("salary", "age"));
        DataFrame output = df.map(formula);
        System.out.println(output);
        assertEquals(df.size(), output.size());
        assertEquals(4, output.ncols());
        assertEquals(10000./38, output.get(0,3));
        assertEquals(null, output.get(1,3));
        assertEquals(230000./48, output.get(2,3));
        assertEquals(null, output.get(3,3));
    }
}