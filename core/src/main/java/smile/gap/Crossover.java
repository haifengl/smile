/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.gap;

import smile.math.MathEx;

/**
 * The types of crossover operation.
 */
public enum Crossover {

    /**
     * Single point crossover - one crossover point is selected, binary
     * string from beginning of chromosome to the crossover point is copied
     * from one parent, the rest is copied from the second parent.
     */
    SINGLE_POINT {
        @Override
        public BitString[] apply(BitString father, BitString mother) {
            int length = father.length;
            byte[] dad = father.bits();
            byte[] mom = mother.bits();
            byte[] son = new byte[length];
            byte[] daughter = new byte[length];

            int point = 0; // crossover point
            while (point == 0) {
                point = MathEx.randomInt(length);
            }

            System.arraycopy(dad, 0, son, 0, point);
            System.arraycopy(mom, point, son, point, length - point);

            System.arraycopy(mom, 0, daughter, 0, point);
            System.arraycopy(dad, point, daughter, point, length - point);

            BitString[] offsprings = {father.newInstance(son), mother.newInstance(daughter)};
            return offsprings;
        }
    },

    /**
     * Two point crossover - two crossover point are selected, binary string
     * from beginning of chromosome to the first crossover point is copied
     * from one parent, the part from the first to the second crossover
     * point is copied from the second parent and the rest is copied from
     * the first parent.
     */
    TWO_POINT {
        @Override
        public BitString[] apply(BitString father, BitString mother) {
            int length = father.length;
            byte[] dad = father.bits();
            byte[] mom = mother.bits();
            byte[] son = new byte[length];
            byte[] daughter = new byte[length];

            int point1 = 0; // first crossover point
            while (point1 == 0 || point1 == length - 1) {
                point1 = MathEx.randomInt(length);
            }

            int point2 = 0; // second crossover point
            while (point2 == point1 || point2 == 0 || point2 == length - 1) {
                point2 = MathEx.randomInt(length);
            }

            if (point2 < point1) {
                int p = point1;
                point1 = point2;
                point2 = p;
            }

            System.arraycopy(dad, 0, son, 0, point1);
            System.arraycopy(mom, point1, son, point1, point2 - point1);
            System.arraycopy(dad, point2, son, point2, length - point2);

            System.arraycopy(mom, 0, daughter, 0, point1);
            System.arraycopy(dad, point1, daughter, point1, point2 - point1);
            System.arraycopy(mom, point2, daughter, point2, length - point2);

            BitString[] offsprings = {father.newInstance(son), mother.newInstance(daughter)};
            return offsprings;
        }
    },

    /**
     * Uniform crossover - bits are randomly copied from the first or from
     * the second parent.
     */
    UNIFORM {
        @Override
        public BitString[] apply(BitString father, BitString mother) {
            int length = father.length;
            byte[] dad = father.bits();
            byte[] mom = mother.bits();
            byte[] son = new byte[length];
            byte[] daughter = new byte[length];

            for (int i = 0; i < length; i++) {
                if (MathEx.random() < 0.5) {
                    son[i] = dad[i];
                    daughter[i] = mom[i];
                } else {
                    son[i] = mom[i];
                    daughter[i] = dad[i];
                }
            }

            BitString[] offsprings = {father.newInstance(son), mother.newInstance(daughter)};
            return offsprings;
        }
    };

    /**
     * Returns a pair of offsprings by crossovering parent chromosomes.
     */
    public abstract BitString[] apply(BitString father, BitString mother);
}
