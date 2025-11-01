/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.model;

/**
 * Behavior post running a cell.
 */
public enum RunBehavior {
    /** Stays the focus on the cell. */
    STAY,
    /** Moves the focus to the next cell. Creates a new cell below if it doesn't exist. */
    NEXT_OR_NEW,
    /** Inserts a new cell below. */
    INSERT_BELOW
}
