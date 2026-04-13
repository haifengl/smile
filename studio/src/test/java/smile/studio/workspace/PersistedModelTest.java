/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.workspace;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PersistedModel}.
 *
 * @author Haifeng Li
 */
public class PersistedModelTest {

    @Test
    public void testRecordAccessors() {
        System.out.println("PersistedModel: record accessors");
        var model = new PersistedModel("iris-rf", "sepallength:double,class:String", "/models/iris.sml");
        assertEquals("iris-rf",                           model.name());
        assertEquals("sepallength:double,class:String",   model.schema());
        assertEquals("/models/iris.sml",                  model.path());
    }

    @Test
    public void testRecordEquality() {
        System.out.println("PersistedModel: record equality");
        var a = new PersistedModel("m1", "s1", "/p1");
        var b = new PersistedModel("m1", "s1", "/p1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testRecordInequality() {
        System.out.println("PersistedModel: record inequality on different path");
        var a = new PersistedModel("m1", "s1", "/p1");
        var b = new PersistedModel("m1", "s1", "/p2");
        assertNotEquals(a, b);
    }

    @Test
    public void testToString() {
        System.out.println("PersistedModel: toString contains all fields");
        var model = new PersistedModel("rf", "x:double", "/models/rf.sml");
        String s = model.toString();
        assertTrue(s.contains("rf"),              "toString should contain name");
        assertTrue(s.contains("x:double"),        "toString should contain schema");
        assertTrue(s.contains("/models/rf.sml"),  "toString should contain path");
    }

    @Test
    public void testNullFieldsAllowed() {
        System.out.println("PersistedModel: null fields are allowed by the record");
        // Records do not prevent null; verify no NPE on construction.
        assertDoesNotThrow(() -> new PersistedModel(null, null, null));
    }
}

