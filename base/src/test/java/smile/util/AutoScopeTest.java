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
package smile.util;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AutoScope} identity-based detach semantics.
 *
 * @author Haifeng Li
 */
public class AutoScopeTest {

    /** Closeable that records whether it was closed. */
    static final class FlagCloseable implements AutoCloseable {
        boolean closed;
        @Override public void close() { closed = true; }
    }

    /** Closeable whose equals/hashCode collide with peers (simulates Tensor handle-address equals). */
    static final class AddressEqualsCloseable implements AutoCloseable {
        final long address;
        boolean closed;
        AddressEqualsCloseable(long address) { this.address = address; }
        @Override public void close() { closed = true; }
        @Override public boolean equals(Object o) {
            return o instanceof AddressEqualsCloseable a && a.address == address;
        }
        @Override public int hashCode() { return Long.hashCode(address); }
    }

    @Test
    public void removeUsesIdentityNotEquals() {
        // Given two distinct resources that compare equal by address
        var zombie = new AddressEqualsCloseable(0x1000L);
        var live = new AddressEqualsCloseable(0x1000L);
        var scope = new AutoScope();
        scope.add(zombie);
        scope.add(live);

        // When we detach the live instance by identity
        scope.remove(live);

        // Then only the zombie remains and is closed; live is not
        scope.close();
        assertTrue(zombie.closed);
        assertFalse(live.closed);
    }

    @Test
    public void closeRunsInReverseOrder() {
        // Given resources that record close order
        var order = new java.util.ArrayList<String>();
        var scope = new AutoScope();
        scope.add(new AutoCloseable() {
            @Override public void close() { order.add("a"); }
        });
        scope.add(new AutoCloseable() {
            @Override public void close() { order.add("b"); }
        });

        // When
        scope.close();

        // Then dependents close before bases
        assertEquals(java.util.List.of("b", "a"), order);
    }

    @Test
    public void closeIsIdempotentForAlreadyClosedEntries() {
        var flag = new FlagCloseable();
        var scope = new AutoScope(flag);
        flag.close();
        assertDoesNotThrow(scope::close);
        assertTrue(flag.closed);
    }
}
