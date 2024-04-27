/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

/**
 * A tuple of 2 elements.
 * @param _1 the first element.
 * @param _2 the second element.
 * @param <T1> the type of first element.
 * @param <T2> the type of second element.
 *
 * @author Haifeng Li
 */
public record Tuple2<T1, T2>(T1 _1, T2 _2) {

}