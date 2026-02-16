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
package smile.agent;

/**
 * Long-term memory stores instructions, rules, and preferences
 * for agents to follow. LLMs are stateless functions. To maintain
 * context across interactions, we need to externalize memory. Long-term
 * memory is persistent and can be retrieved and updated over time.
 *
 * @author Haifeng Li
 */
public interface Memory {
    /**
     * Returns the name of the memory.
     * @return the name of the memory.
     */
    String name();
    /**
     * Returns the description of the memory.
     * @return the description of the memory.
     */
    String description();
    /**
     * Returns the content of the memory.
     * @return the content of the memory.
     */
    String content();
}
