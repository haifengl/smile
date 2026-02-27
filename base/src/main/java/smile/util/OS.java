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
 * Operating system utility functions.
 *
 * @author Haifeng Li
 */
public interface OS {
    String name = System.getProperty("os.name").toLowerCase();

    /**
     * Returns true if the operating system is Windows.
     */
    static boolean isWindows() {
        return name.contains("win");
    }

    /**
     * Returns true if the operating system is MacOS.
     */
    static boolean isMacOS() {
        return name.contains("mac");
    }

    /**
     * Returns true if the operating system is Linux or Unix.
     */
    static boolean isUnix() {
        return name.contains("nix") || name.contains("nux") || name.contains("aix");
    }
}
