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


/**
 * AutoScope allows for predictable, deterministic resource deallocation.
 * AutoScope can be closed explicitly or be best used with try-with-resources
 * statement. Closing an AutoScope will cause all the resources
 * associated with that scope to be closed.
 */
public class AutoScope implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AutoScope.class);
    /**
     * Identity key: scopes hold tensors whose equals() compares native addresses (reusable once freed), and
     * membership must be O(1) because one scope can hold tens of thousands of temporaries (a model forward).
     */
    private record Ref(AutoCloseable resource) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Ref r && r.resource == resource;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(resource);
        }
    }

    /** Resources in registration order (closed in reverse). */
    private final java.util.LinkedHashSet<Ref> resources = new java.util.LinkedHashSet<>();

    /**
     * Constructors.
     * @param resources the resources to be added to this scope.
     */
    public AutoScope(AutoCloseable... resources) {
        for (var resource : resources) {
            add(resource);
        }
    }

    /**
     * Adds resource to this scope.
     * @param resource the resource to be added to this scope.
     * @param <T> the type of resource.
     * @return the resource object.
     */
    public <T extends AutoCloseable> T add(T resource) {
        // Idempotent by reference: promoteToParent + explicit add must not
        // register the same tensor twice (double-close on scope teardown).
        this.resources.add(new Ref(resource));
        return resource;
    }

    /**
     * Detaches a resource from this scope by reference identity.
     *
     * <p>Must not use {@link Object#equals(Object)}: {@code Tensor.equals}
     * compares native handle addresses, so after a closed tensor's address is
     * reused by a new allocation, {@code List.remove(Object)} can detach the
     * wrong entry and leave the live tensor on the scope to be freed by
     * {@link #close()}.
     *
     * @param resource the resource to detach.
     */
    public void remove(AutoCloseable resource) {
        resources.remove(new Ref(resource));
    }

    @Override
    public void close() {
        // Snapshot + clear first so resource.close() may safely detach itself.
        AutoCloseable[] snapshot = resources.stream().map(Ref::resource).toArray(AutoCloseable[]::new);
        resources.clear();
        // Reverse order: dependents (views) before bases, matching try-with.
        for (int i = snapshot.length - 1; i >= 0; i--) {
            try {
                snapshot[i].close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
