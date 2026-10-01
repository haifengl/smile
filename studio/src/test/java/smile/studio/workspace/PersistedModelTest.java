/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
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

