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
package smile.association;

import java.util.Arrays;

/**
 * A set of items. The support supp(X) of an item set X is defined as the
 * proportion of transactions in the data set which contain the item set.
 * In this class, the support is actually the raw frequency rather than the
 * ratio.
 *
 * @param items The set of items.
 * @param support The associated support value.
 * @author Haifeng Li
 */
public record ItemSet(int[] items, int support) {

    @Override
    public boolean equals(Object o) {
        if (o instanceof ItemSet a) {
            return support == a.support && Arrays.equals(items, a.items);
        }
        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Arrays.hashCode(this.items);
        hash = 23 * hash + this.support;
        return hash;
    }

    @Override
    public String toString() {
        return "ItemSet(" +
                Arrays.toString(items) +
                ", support=" +
                support +
                ')';
    }
}
