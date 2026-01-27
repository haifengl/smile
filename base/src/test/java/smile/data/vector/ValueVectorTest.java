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
package smile.data.vector;

import java.time.*;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.util.Index;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ValueVectorTest {

    public ValueVectorTest() {
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
    public void testFactory() {
        System.out.println("of");
        var doubles = ValueVector.of("A", 1.0, 2.0, 3.0);
        assertEquals(DataTypes.DoubleType, doubles.dtype());

        var instants = ValueVector.of("B", Instant.now());
        assertEquals(DataTypes.DateTimeType, instants.dtype());

        var datetimes = ValueVector.of("B", LocalDateTime.now());
        assertEquals(DataTypes.DateTimeType, datetimes.dtype());

        var dates = ValueVector.of("B", LocalDate.now());
        assertEquals(DataTypes.DateType, dates.dtype());

        var times = ValueVector.of("B", LocalTime.now());
        assertEquals(DataTypes.TimeType, times.dtype());

        var cat = ValueVector.nominal("C", "test", "train", "test", "train");
        assertTrue(cat instanceof ByteVector);
        assertEquals(DataTypes.ByteType, cat.dtype());
        assertTrue(cat.measure() instanceof NominalScale);

        var strings = ValueVector.of("D",
                "this is a string vector",
                "Nominal/ordinal vectors store data as integers internally");
        assertEquals(DataTypes.StringType, strings.dtype());

        var arrayVector = ObjectVector.of("E", Index.range(0, 4).toArray(), new int[]{3, 3, 3, 3});
        assertEquals(DataTypes.IntArrayType, arrayVector.dtype());
    }
}
