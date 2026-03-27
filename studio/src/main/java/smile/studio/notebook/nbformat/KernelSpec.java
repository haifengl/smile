/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.notebook.nbformat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Identifies the kernel (execution engine) associated with the notebook.
 *
 * @param displayName  the human-readable kernel name displayed in the UI.
 * @param language     the programming language of the kernel (e.g. {@code "python"}, {@code "scala"}).
 * @param name         the machine name used to locate and start the kernel (e.g. {@code "python3"}).
 *
 * @author Haifeng Li
 */
public record KernelSpec(
        @JsonProperty("display_name") String displayName,
        @JsonProperty("language") String language,
        @JsonProperty("name") String name
) {
}

