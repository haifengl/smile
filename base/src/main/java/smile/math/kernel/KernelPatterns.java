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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.kernel;

import java.util.regex.Pattern;
import static smile.util.Regex.DOUBLE_REGEX;
import static smile.util.Regex.INTEGER_REGEX;

/** Package only interface to hold regex patterns. */
interface KernelPatterns {
    Pattern linear = Pattern.compile("linear(?:kernel)?(?:\\(\\))?");
    Pattern polynomial = Pattern.compile(
            String.format("polynomial(?:kernel)?\\((%s),\\s*(%s),\\s*(%s)\\)", INTEGER_REGEX, DOUBLE_REGEX, DOUBLE_REGEX));
    Pattern gaussian = Pattern.compile(
            String.format("gaussian(?:kernel)?\\((%s)\\)", DOUBLE_REGEX));
    Pattern matern = Pattern.compile(
            String.format("matern(?:kernel)?\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
    Pattern laplacian = Pattern.compile(
            String.format("laplacian(?:kernel)?\\((%s)\\)", DOUBLE_REGEX));
    Pattern tanh = Pattern.compile(
            String.format("tanh(?:kernel)?\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
    Pattern thinPlateSpline = Pattern.compile(
            String.format("tps(?:kernel)?\\((%s)\\)", DOUBLE_REGEX));
    Pattern pearson = Pattern.compile(
            String.format("pearson(?:kernel)?\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
    Pattern hellinger = Pattern.compile("hellinger(?:kernel)?(?:\\(\\))?");
}
