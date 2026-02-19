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

import java.util.List;
import tools.jackson.databind.node.ObjectNode;
/**
 * Tools enable models to interact with external systems, such as
 * querying databases, calling APIs, or performing computations.
 * Each tool is uniquely identified by a name and includes metadata
 * describing its schema.
 *
 * @author Haifeng Li
 */
public record Tool(String name,
                   String description,
                   ObjectNode inputSchema,
                   List<Object> inputExamples) {
}
