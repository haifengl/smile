/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.util.List;
import java.util.LinkedList;

/**
 * AutoScope allows for predictable, deterministic resource deallocation.
 * AutoScope can be closed explicitly or be best used with try-with-resources
 * Statement. Closing an AutoScope will cause all the cleanup actions
 * associated with that scope to be called.
 */
public class AutoScope implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AutoScope.class);
    private final List<AutoCloseable> resources = new LinkedList<>();

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
        this.resources.add(resource);
        return resource;
    }

    /**
     * Detaches resources from this scope.
     * @param resource the resources to be detached from this scope.
     */
    public void remove(AutoCloseable resource) {
        this.resources.remove(resource);
    }

    @Override
    public void close() {
        for (var resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        resources.clear();
    }
}
