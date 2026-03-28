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
package smile.studio.notebook;

/**
 * The type of notebook cells.
 *
 * @author Haifeng Li
 */
public enum CellType {
    /**
     * Source code.
     */
    Code("code"),
    /**
     * Narrative text or documentation in Markdown format.
     */
    Markdown("markdown"),
    /**
     * Raw text is not evaluated or rendered by the kernel.
     */
    Raw("raw");

    /** The cell type identifier. */
    private final String value;

    /**
     * Constructor.
     * @param value the cell type identifier.
     */
    CellType(String value) {
        this.value = value;
    }

    /**
     * Returns the cell type identifier.
     * @return the cell type identifier.
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
