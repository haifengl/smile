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
package smile.llm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Role} enum.
 *
 * @author Haifeng Li
 */
public class RoleTest {
    @Test
    public void testGivenRoleWhenAllValuesEnumeratedThenFourExist() {
        assertEquals(4, Role.values().length);
    }

    @Test
    public void testGivenRoleSystemWhenNameCalledThenIsSystem() {
        assertEquals("system", Role.system.name());
    }

    @Test
    public void testGivenRoleValueOfWhenCalledThenReturnsCorrectEnum() {
        assertEquals(Role.user, Role.valueOf("user"));
        assertEquals(Role.assistant, Role.valueOf("assistant"));
        assertEquals(Role.ipython, Role.valueOf("ipython"));
    }
}
