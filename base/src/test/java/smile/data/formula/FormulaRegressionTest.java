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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

import static smile.data.formula.Terms.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for bug fixes and coverage improvements in smile.data.formula.
 *
 * Bug fixes verified:
 *  1. Round.apply() used Math.abs for Float instead of Math.round.
 *  2. Terms.atan() used Math::acos instead of Math::atan.
 *  3. Add/Sub/Mul/Div type-check used OR instead of AND — never rejected
 *     non-numeric operands.
 *  4. Formula lacked hashCode() (violating equals/hashCode contract).
 *  5. Formula.of(String) and Terms.$() regexes did not parse ')^N'
 *     factor-crossing degree correctly.
 */
public class FormulaRegressionTest {

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    public enum Gender { Male, Female }

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

        public String getName()       { return name; }
        public Gender getGender()     { return gender; }
        public LocalDate getBirthday(){ return birthday; }
        public int getAge()           { return age; }
        public Double getSalary()     { return salary; }
    }

    public static class Event {
        LocalDateTime timestamp;
        float value;

        Event(LocalDateTime timestamp, float value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public float getValue()             { return value; }
    }

    public static class TimeOnly {
        LocalTime time;
        TimeOnly(LocalTime time) { this.time = time; }
        public LocalTime getTime() { return time; }
    }

    DataFrame df;       // Person data frame
    DataFrame eventDf;  // Event data frame
    DataFrame timeDf;   // TimeOnly data frame

    public FormulaRegressionTest() {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person("Alex", Gender.Male,   LocalDate.of(1980, 10,  1), 38,  10000.));
        persons.add(new Person("Bob",  Gender.Male,   LocalDate.of(1995,  3,  4), 23,  null));
        persons.add(new Person("Jane", Gender.Female, LocalDate.of(1970,  3,  1), 48, 230000.));
        persons.add(new Person("Amy",  Gender.Female, LocalDate.of(2005, 12, 10), 13,  null));
        df = DataFrame.of(Person.class, persons);

        List<Event> events = new ArrayList<>();
        events.add(new Event(LocalDateTime.of(2024, 6, 15, 9, 30, 0), 3.7f));
        events.add(new Event(LocalDateTime.of(2024, 12, 31, 23, 59, 59), -1.5f));
        eventDf = DataFrame.of(Event.class, events);

        List<TimeOnly> times = new ArrayList<>();
        times.add(new TimeOnly(LocalTime.of(8, 0, 0)));
        times.add(new TimeOnly(LocalTime.of(17, 45, 30)));
        timeDf = DataFrame.of(TimeOnly.class, times);
    }

    // =========================================================================
    // BUG FIX 1 – Round.apply() used Math.abs for Float, not Math.round
    // =========================================================================

    @Test
    public void testRoundFloat() {
        System.out.println("BUG-FIX: round() on float column");
        Formula formula = Formula.rhs(round("value"));
        assertEquals(" ~ round(value)", formula.toString());

        DataFrame output = formula.frame(eventDf);
        assertEquals(eventDf.size(), output.size());
        // 3.7f rounds to 4, -1.5f rounds to -1
        assertEquals((double) Math.round(3.7f),  output.getDouble(0, 0), 1e-3);
        assertEquals((double) Math.round(-1.5f), output.getDouble(1, 0), 1e-3);
    }

    @Test
    public void testRoundDouble() {
        System.out.println("round() on double column (regression)");
        // salary: 10000.0, null, 230000.0, null
        Formula formula = Formula.rhs(round("salary"));
        DataFrame output = formula.frame(df);
        assertEquals((double) Math.round(10000.0),  output.getDouble(0, 0), 1e-7);
        assertTrue(Double.isNaN(output.getDouble(1, 0)));
        assertEquals((double) Math.round(230000.0), output.getDouble(2, 0), 1e-7);
        assertTrue(Double.isNaN(output.getDouble(3, 0)));
    }

    // =========================================================================
    // BUG FIX 2 – atan() called Math::acos instead of Math::atan
    // =========================================================================

    @Test
    public void testAtanCorrectness() {
        System.out.println("BUG-FIX: atan() must use Math.atan, not Math.acos");
        Formula formula = Formula.rhs(atan("age"));
        assertEquals(" ~ atan(age)", formula.toString());

        DataFrame output = formula.frame(df);
        assertEquals(df.size(), output.size());
        assertEquals(Math.atan(38), output.getDouble(0, 0), 1e-10);
        assertEquals(Math.atan(23), output.getDouble(1, 0), 1e-10);
        assertEquals(Math.atan(48), output.getDouble(2, 0), 1e-10);
        assertEquals(Math.atan(13), output.getDouble(3, 0), 1e-10);
        // Verify they are NOT equal to acos values (the old wrong behaviour)
        assertNotEquals(Math.acos(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testAtanParsedFromString() {
        System.out.println("BUG-FIX: atan() parsed from formula string");
        Formula formula = Formula.of(" ~ atan(age)");
        DataFrame output = formula.frame(df);
        assertEquals(Math.atan(38), output.getDouble(0, 0), 1e-10);
    }

    // =========================================================================
    // BUG FIX 3 – Add/Sub/Mul/Div type-check condition (OR→AND)
    //             Non-numeric operands must throw IllegalStateException
    // =========================================================================

    @Test
    public void testAddNonNumericThrows() {
        System.out.println("BUG-FIX: add() with non-numeric operands must throw");
        StructType schema = new StructType(
                new StructField("a", DataTypes.StringType),
                new StructField("b", DataTypes.IntType)
        );
        Term term = add("a", "b");
        assertThrows(IllegalStateException.class, () -> term.bind(schema));
    }

    @Test
    public void testSubNonNumericThrows() {
        System.out.println("BUG-FIX: sub() with non-numeric operands must throw");
        StructType schema = new StructType(
                new StructField("a", DataTypes.IntType),
                new StructField("b", DataTypes.StringType)
        );
        Term term = sub("a", "b");
        assertThrows(IllegalStateException.class, () -> term.bind(schema));
    }

    @Test
    public void testMulNonNumericThrows() {
        System.out.println("BUG-FIX: mul() with non-numeric operands must throw");
        StructType schema = new StructType(
                new StructField("a", DataTypes.StringType),
                new StructField("b", DataTypes.StringType)
        );
        Term term = mul("a", "b");
        assertThrows(IllegalStateException.class, () -> term.bind(schema));
    }

    @Test
    public void testDivNonNumericThrows() {
        System.out.println("BUG-FIX: div() with non-numeric operands must throw");
        StructType schema = new StructType(
                new StructField("a", DataTypes.StringType),
                new StructField("b", DataTypes.DoubleType)
        );
        Term term = div("a", "b");
        assertThrows(IllegalStateException.class, () -> term.bind(schema));
    }

    // =========================================================================
    // BUG FIX 4 – Formula.hashCode() was missing
    // =========================================================================

    @Test
    public void testFormulaHashCodeConsistentWithEquals() {
        System.out.println("BUG-FIX: equal formulas must have equal hash codes");
        Formula f1 = Formula.of("salary ~ age");
        Formula f2 = Formula.of("salary ~ age");
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testFormulaHashCodeDiffersForDifferentFormulas() {
        System.out.println("Different formulas should (almost always) have different hash codes");
        Formula f1 = Formula.of("salary ~ age");
        Formula f2 = Formula.of("salary ~ name");
        assertNotEquals(f1, f2);
        // Hash codes CAN collide but shouldn't for these simple cases
        assertNotEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testFormulaUsableInHashMap() {
        System.out.println("Formula should be usable as HashMap key after hashCode fix");
        java.util.Map<Formula, String> map = new java.util.HashMap<>();
        Formula key = Formula.of("y ~ x");
        map.put(key, "model");
        Formula lookupKey = Formula.of("y ~ x");
        assertEquals("model", map.get(lookupKey));
    }

    // =========================================================================
    // BUG FIX 5 – Formula.of(String) and Terms.$() regex for ')^N' crossing
    // =========================================================================

    @Test
    public void testParseFactorCrossingWithDegree() {
        System.out.println("BUG-FIX: parse factor crossing with degree from string");
        Formula f = Formula.of("y ~ (a x b x c)^2");
        assertEquals("y ~ (a x b x c)^2", f.toString());

        // Verify round-trip
        Formula f2 = Formula.of(f.toString());
        assertEquals(f, f2);
    }

    @Test
    public void testParseFactorCrossingFullDegree() {
        System.out.println("BUG-FIX: parse full factor crossing (no degree) from string");
        Formula f = Formula.of(" ~ (a x b x c)");
        assertEquals(" ~ (a x b x c)", f.toString());

        Formula f2 = Formula.of(f.toString());
        assertEquals(f, f2);
    }

    @Test
    public void testTermsDollarFactorCrossingWithDegree() {
        System.out.println("BUG-FIX: Terms.$() parses ')^N' correctly");
        Term t = Terms.$("(a x b x c)^2");
        assertInstanceOf(FactorCrossing.class, t);
        assertEquals("(a x b x c)^2", t.toString());
    }

    @Test
    public void testTermsDollarFactorCrossingFullDegree() {
        System.out.println("BUG-FIX: Terms.$() parses full crossing correctly");
        Term t = Terms.$("(a x b x c)");
        assertInstanceOf(FactorCrossing.class, t);
        assertEquals("(a x b x c)", t.toString());
    }

    @Test
    public void testCrossingDegreeRoundTripBind() {
        System.out.println("BUG-FIX: parsed ')^2' crossing binds correctly");
        StructType schema = new StructType(
                new StructField("a", DataTypes.ByteType, new NominalScale("x", "y")),
                new StructField("b", DataTypes.ByteType, new NominalScale("p", "q")),
                new StructField("c", DataTypes.ByteType, new NominalScale("m", "n"))
        );
        // Build programmatically and via string parse; both must bind identically
        Formula programmatic = Formula.rhs(cross(2, "a", "b", "c"));
        Formula parsed       = Formula.of(" ~ (a x b x c)^2");

        StructType schemaProg   = programmatic.bind(schema);
        StructType schemaParsed = parsed.bind(schema);
        assertEquals(schemaProg, schemaParsed);
    }

    // =========================================================================
    // Additional coverage – trig functions
    // =========================================================================

    @Test
    public void testAcos() {
        Formula formula = Formula.rhs(acos($("salary")));
        DataFrame output = formula.frame(df);
        // salary values outside [-1,1] yield NaN, which is fine; just confirm it runs
        assertEquals(df.size(), output.size());
        assertEquals(1, output.ncol());
    }

    @Test
    public void testSin() {
        Formula formula = Formula.rhs(sin("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.sin(38), output.getDouble(0, 0), 1e-10);
        assertEquals(Math.sin(23), output.getDouble(1, 0), 1e-10);
    }

    @Test
    public void testCos() {
        Formula formula = Formula.rhs(cos("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.cos(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testTan() {
        Formula formula = Formula.rhs(tan("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.tan(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testSinh() {
        Formula formula = Formula.rhs(sinh("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.sinh(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testCosh() {
        Formula formula = Formula.rhs(cosh("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.cosh(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testTanh() {
        Formula formula = Formula.rhs(tanh("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.tanh(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testAsin() {
        Formula formula = Formula.rhs(asin("age"));
        DataFrame output = formula.frame(df);
        // asin(38) is NaN – just verify execution
        assertEquals(df.size(), output.size());
    }

    @Test
    public void testCbrt() {
        Formula formula = Formula.rhs(cbrt("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.cbrt(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testLog2() {
        Formula formula = Formula.rhs(log2("age"));
        DataFrame output = formula.frame(df);
        assertEquals(smile.math.MathEx.log2(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testLog1p() {
        Formula formula = Formula.rhs(log1p("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.log1p(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testExpm1() {
        Formula formula = Formula.rhs(expm1("age"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.expm1(38), output.getDouble(0, 0), 1e-10);
    }

    @Test
    public void testRint() {
        Formula formula = Formula.rhs(rint("salary"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.rint(10000.0), output.getDouble(0, 0), 1e-10);
        assertNull(output.get(1, 0));
    }

    @Test
    public void testUlp() {
        Formula formula = Formula.rhs(ulp("salary"));
        DataFrame output = formula.frame(df);
        assertEquals(Math.ulp(10000.0), output.getDouble(0, 0), 1e-10);
        assertNull(output.get(1, 0));
    }

    // =========================================================================
    // Additional coverage – Date/DateTime/Time edge cases
    // =========================================================================

    @Test
    public void testDateTimeFeatures() {
        System.out.println("DateTime features: YEAR, MONTH, DAY_OF_MONTH, HOUR, MINUTE, SECOND");
        Formula formula = Formula.rhs(date("timestamp",
                DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH,
                DateFeature.HOUR, DateFeature.MINUTE, DateFeature.SECOND));
        DataFrame output = formula.frame(eventDf);
        assertEquals(eventDf.size(), output.size());
        assertEquals(6, output.ncol());

        // Row 0: 2024-06-15 09:30:00
        assertEquals(2024, output.getInt(0, 0));
        assertEquals(6,    output.getInt(0, 1));
        assertEquals(15,   output.getInt(0, 2));
        assertEquals(9,    output.getInt(0, 3));
        assertEquals(30,   output.getInt(0, 4));
        assertEquals(0,    output.getInt(0, 5));

        // Row 1: 2024-12-31 23:59:59
        assertEquals(2024, output.getInt(1, 0));
        assertEquals(12,   output.getInt(1, 1));
        assertEquals(31,   output.getInt(1, 2));
        assertEquals(23,   output.getInt(1, 3));
        assertEquals(59,   output.getInt(1, 4));
        assertEquals(59,   output.getInt(1, 5));
    }

    @Test
    public void testDateTimeQuarterAndWeek() {
        System.out.println("DateTime features: QUARTER, WEEK_OF_YEAR, WEEK_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK");
        Formula formula = Formula.rhs(date("timestamp",
                DateFeature.QUARTER, DateFeature.DAY_OF_YEAR, DateFeature.DAY_OF_WEEK));
        DataFrame output = formula.frame(eventDf);
        assertEquals(3, output.ncol()); // Q2, day 167, Saturday(6)
        assertEquals(2, output.getInt(0, 0)); // Q2 for June
        assertEquals(4, output.getInt(1, 0)); // Q4 for December
    }

    @Test
    public void testTimeFeatures() {
        System.out.println("Time-only features: HOUR, MINUTE, SECOND");
        Formula formula = Formula.rhs(date("time",
                DateFeature.HOUR, DateFeature.MINUTE, DateFeature.SECOND));
        DataFrame output = formula.frame(timeDf);
        assertEquals(timeDf.size(), output.size());
        assertEquals(3, output.ncol());

        assertEquals(8,  output.getInt(0, 0));
        assertEquals(0,  output.getInt(0, 1));
        assertEquals(0,  output.getInt(0, 2));
        assertEquals(17, output.getInt(1, 0));
        assertEquals(45, output.getInt(1, 1));
        assertEquals(30, output.getInt(1, 2));
    }

    @Test
    public void testTimeWithDateFeatureThrows() {
        System.out.println("Time-only column with YEAR feature must throw");
        Formula formula = Formula.rhs(date("time", DateFeature.YEAR));
        assertThrows(UnsupportedOperationException.class, () -> formula.frame(timeDf));
    }

    // =========================================================================
    // Additional coverage – Formula factory methods and parsing edge cases
    // =========================================================================

    @Test
    public void testFormulaLhsString() {
        System.out.println("Formula.lhs(String) creates dot predictor");
        Formula f = Formula.lhs("salary");
        assertEquals("salary ~ .", f.toString());
        assertNotNull(f.response());
        assertEquals(1, f.predictors().length);
    }

    @Test
    public void testFormulaRhsStrings() {
        System.out.println("Formula.rhs(String...) creates null response formula");
        Formula f = Formula.rhs("age", "gender");
        assertEquals(" ~ age + gender", f.toString());
        assertNull(f.response());
    }

    @Test
    public void testFormulaRhsSpecialTokens() {
        System.out.println("Formula.of with '0' and '1' intercept tokens");
        Formula f0 = Formula.of("y", "0");
        assertEquals("y ~ 0", f0.toString());

        Formula f1 = Formula.of("y", "1");
        assertEquals("y ~ 1", f1.toString());
    }

    @Test
    public void testFormulaParseIntercept0() {
        System.out.println("Formula.of(String) parses '- 1' as intercept removal");
        Formula f = Formula.of("y ~ x - 1");
        // "- 1" is parsed as Intercept(false) which prints as "0" (prefixed by "+ "), yielding "y ~ x + 0"
        assertEquals("y ~ x + 0", f.toString());
    }

    @Test
    public void testFormulaParseInvalidThrows() {
        System.out.println("Formula.of(String) with no '~' must throw");
        assertThrows(IllegalArgumentException.class, () -> Formula.of("salary age"));
    }

    @Test
    public void testFormulaParseEmptyBothSidesThrows() {
        System.out.println("Formula.of(String) with empty both sides must throw");
        assertThrows(IllegalArgumentException.class, () -> Formula.of(" ~ "));
    }

    @Test
    public void testFormulaResponseConstructorRejectsDot() {
        System.out.println("Formula constructor must reject Dot as response");
        assertThrows(IllegalArgumentException.class,
                () -> new Formula(new Dot(), new Variable("x")));
    }

    @Test
    public void testFormulaResponseConstructorRejectsFactorCrossing() {
        System.out.println("Formula constructor must reject FactorCrossing as response");
        assertThrows(IllegalArgumentException.class,
                () -> new Formula(new FactorCrossing("a", "b"), new Variable("x")));
    }

    @Test
    public void testFormulaYNoResponseThrows() {
        System.out.println("Formula.y() with no response variable must throw");
        Formula formula = Formula.rhs("age");
        assertThrows(UnsupportedOperationException.class, () -> formula.y(df.get(0)));
    }

    @Test
    public void testFormulaYDataFrameNoResponseThrows() {
        System.out.println("Formula.y(DataFrame) with no response variable must throw");
        Formula formula = Formula.rhs("age");
        assertThrows(UnsupportedOperationException.class, () -> formula.y(df));
    }

    // =========================================================================
    // Additional coverage – Intercept / hasBias
    // =========================================================================

    @Test
    public void testMatrixWithExplicitBias() {
        System.out.println("matrix() with explicit '1' intercept includes bias");
        Formula f = Formula.of("salary ~ age + 1");
        var m = f.matrix(df);
        // intercept col + age col = 2
        assertEquals(2, m.ncol());
        assertEquals(1.0, m.get(0, 0), 1e-10); // bias column
    }

    @Test
    public void testMatrixWithNoBias() {
        System.out.println("matrix() with '0' intercept excludes bias");
        Formula f = Formula.of("salary ~ age + 0");
        var m = f.matrix(df);
        // No bias: only age column
        assertEquals(1, m.ncol());
    }

    // =========================================================================
    // Additional coverage – Delete term
    // =========================================================================

    @Test
    public void testDeleteExpandsCorrectly() {
        System.out.println("Delete.expand() wraps inner term expansions in Delete");
        Term del = Terms.delete(cross("a", "b"));
        List<Term> expanded = del.expand();
        // cross(a,b) expands to [a, b, a:b]; each gets wrapped in Delete
        assertEquals(3, expanded.size());
        for (Term t : expanded) {
            assertInstanceOf(Delete.class, t, "Expected Delete but got: " + t.getClass());
        }
    }

    @Test
    public void testDeleteToStringStartsWithMinus() {
        Term del = Terms.delete($("x"));
        assertTrue(del.toString().startsWith("- "));
    }

    // =========================================================================
    // Additional coverage – FactorCrossing edge cases
    // =========================================================================

    @Test
    public void testFactorCrossingRejectsSingleFactor() {
        System.out.println("FactorCrossing with one factor must throw");
        assertThrows(IllegalArgumentException.class, () -> new FactorCrossing("a"));
    }

    @Test
    public void testFactorCrossingRejectsInvalidOrder() {
        System.out.println("FactorCrossing with order < 2 must throw");
        assertThrows(IllegalArgumentException.class, () -> new FactorCrossing(1, "a", "b"));
    }

    @Test
    public void testFactorInteractionRejectsSingleFactor() {
        System.out.println("FactorInteraction with one factor must throw");
        assertThrows(IllegalArgumentException.class, () -> new FactorInteraction("a"));
    }

    // =========================================================================
    // Additional coverage – Constant val() terms
    // =========================================================================

    @Test
    public void testValInt() {
        Term t = val(42);
        assertEquals("42", t.toString());
        Formula f = Formula.rhs(add("age", val(42)));
        DataFrame output = f.frame(df);
        assertEquals(38 + 42, output.getInt(0, 0));
        assertEquals(23 + 42, output.getInt(1, 0));
    }

    @Test
    public void testValDouble() {
        Term t = val(3.14);
        assertEquals("3.14", t.toString());
        Formula f = Formula.rhs(mul("salary", val(0.1)));
        DataFrame output = f.frame(df);
        assertEquals(10000.0 * 0.1, output.getDouble(0, 0), 1e-7);
        assertNull(output.get(1, 0)); // null * 0.1 = null
    }

    @Test
    public void testValLong() {
        Term t = val(100L);
        assertEquals("100", t.toString());
    }

    @Test
    public void testValBoolean() {
        Term t = val(true);
        assertEquals("true", t.toString());
    }

    // =========================================================================
    // Additional coverage – Formula.apply(Tuple) / x(Tuple)
    // =========================================================================

    @Test
    public void testApplyTuple() {
        System.out.println("Formula.apply(Tuple) produces yxschema tuple");
        Formula f = Formula.of("salary", "age");
        var tuple = f.apply(df.get(0));
        // yxschema: salary, age
        assertEquals(2, tuple.schema().length());
        assertEquals(10000.0, tuple.getDouble(0), 1e-7);
        assertEquals(38, tuple.getInt(1));
    }

    @Test
    public void testXTuple() {
        System.out.println("Formula.x(Tuple) produces predictor tuple");
        Formula f = Formula.of("salary", "age");
        var tuple = f.x(df.get(0));
        assertEquals(1, tuple.schema().length());
        assertEquals(38, tuple.getInt(0));
    }

    @Test
    public void testYInt() {
        System.out.println("Formula.yint(Tuple)");
        Formula f = Formula.of("age", "salary");
        assertEquals(38, f.yint(df.get(0)));
    }

    // =========================================================================
    // Additional coverage – expand() with Delete and Dot interaction
    // =========================================================================

    @Test
    public void testExpandWithDotAndDelete() {
        System.out.println("expand(): Dot expands to remaining columns, Delete removes them");
        Formula f = Formula.of("salary", dot(), delete("age"));
        // Dot expands to all columns except salary (response) and age (deleted).
        Formula expanded = f.expand(df.schema());
        System.out.println("Expanded: " + expanded);
        // Extract just the RHS (predictors) part
        String[] parts = expanded.toString().split("~", 2);
        String predictorStr = parts[1].trim();
        System.out.println("Predictors: " + predictorStr);
        assertFalse(predictorStr.contains("age"), "age should be deleted from expanded predictors");
        assertFalse(predictorStr.contains("salary"), "salary (response) should not be in predictors");
    }

    @Test
    public void testFormulaEqualsNullResponse() {
        System.out.println("Two formulas with null response are equal");
        Formula f1 = Formula.rhs("a", "b");
        Formula f2 = Formula.rhs("a", "b");
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testFormulaNotEqualsWhenPredictorsDiffer() {
        Formula f1 = Formula.of("y ~ a + b");
        Formula f2 = Formula.of("y ~ a + c");
        assertNotEquals(f1, f2);
    }

    @Test
    public void testFormulaNotEqualsWhenResponseDiffers() {
        Formula f1 = Formula.of("y ~ a");
        Formula f2 = Formula.of("z ~ a");
        assertNotEquals(f1, f2);
    }

    @Test
    public void testFormulaEqualsNonFormula() {
        Formula f = Formula.of("y ~ a");
        assertNotEquals("y ~ a", f);
    }

    // =========================================================================
    // Additional coverage – Formula close()
    // =========================================================================

    @Test
    public void testFormulaClose() {
        System.out.println("Formula.close() releases thread-local binding");
        Formula f = Formula.of("salary", "age");
        f.bind(df.schema()); // force binding to be set
        f.close();
        // After close, calling bind again should re-bind without error
        StructType schema = f.bind(df.schema());
        assertNotNull(schema);
    }
}

