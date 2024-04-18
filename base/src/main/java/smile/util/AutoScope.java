/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
    private List<AutoCloseable> resources = new LinkedList<>();

    /**
     * Constructors.
     * @param resources the resources to be attached to this scope.
     */
    public AutoScope(AutoCloseable... resources) {
        attach(resources);
    }

    /**
     * Attaches resources to this Scope.
     * @param resources the resources to be attached to this scope.
     * @return this object.
     */
    public AutoScope attach(AutoCloseable... resources) {
        for (var resource : resources) {
            if (!this.resources.contains(resource)) {
                this.resources.add(resource);
            }
        }
        return this;
    }

    /**
     * Detaches resources from this Scope.
     * @param resources the resources to be detached from this scope.
     * @return this object.
     */
    public AutoScope detach(AutoCloseable... resources) {
        for (var resource : resources) {
            this.resources.remove(resource);
        }
        return this;
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
    }
}
