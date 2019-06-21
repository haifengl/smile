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

package smile.association;

import java.util.Arrays;

/**
 * A set of items. The support supp(X) of an item set X is defined as the
 * proportion of transactions in the data set which contain the item set.
 * In this class, the support is actually the raw frequency rather than the
 * ratio.
 *
 * @author Haifeng Li
 */
public class ItemSet {

    /**
     * The set of items.
     */
    public final int[] items;
    /**
     * The associated support of item set.
     */
    public final int support;

    /**
     * Constructor.
     * @param items The set of items.
     * @param support The associated support value.
     */
    public ItemSet(int[] items, int support) {
        this.items = items;
        this.support = support;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ItemSet) {
            ItemSet a = (ItemSet) o;
            if (support != a.support) {
                return false;
            }
            
            if (items.length != a.items.length) {
                return false;
            }
            
            for (int i = 0; i < items.length; i++) {
                if (items[i] != a.items[i]) {
                    return false;
                }
            }
            
            return true;
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
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.length; i++) {
            sb.append(items[i]);
            sb.append(' ');
        }
        
        sb.append('(');
        sb.append(support);
        sb.append(')');
        
        return sb.toString();
    }
}
