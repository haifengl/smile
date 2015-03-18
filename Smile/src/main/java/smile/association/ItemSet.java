/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
