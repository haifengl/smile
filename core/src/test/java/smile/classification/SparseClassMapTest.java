/*******************************************************************************
 * Copyright (c) 2019 Haifeng Li
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
package smile.classification;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SparseClassMapTest {
    @Test
    public void DenseLabels() {
        SparseClassMap map = new SparseClassMap(new int[] { 9, 8, 7, 1, 2, 3, 6, 5, 4, 0, 0, 0 });
        assertEquals(map.numberOfClasses(), 10);
        assertEquals(map.maxSparseLabel(), 9);
        assertTrue(map.isIdentity());
        for (int i = 0; i < 10; i++) {
            assertEquals(map.sparseLabelToDenseLabel(i), i);
            assertEquals(map.denseLabelToSparseLabel(i), i);
        }
    }

    @Test
    public void SparseLabels() {
        SparseClassMap map = new SparseClassMap(new int[] { 9, 7, 1, 3, 5, 1, 7, 9 });
        assertEquals(map.numberOfClasses(), 5);
        assertEquals(map.maxSparseLabel(), 9);
        assertFalse(map.isIdentity());
        for (int i = 0; i < 5; i++) {
            assertEquals(map.denseLabelToSparseLabel(i), 2 * i + 1);
            assertEquals(map.sparseLabelToDenseLabel(2 * i + 1), i);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void NegativeLabel() {
        new SparseClassMap(new int[] { 9, 7, 1, 3, 5, -1, 7, 9 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void BadDenseLabel() {
        SparseClassMap map = new SparseClassMap(new int[] { 9, 7, 1, 3, 5, 1, 7, 9 });
        map.denseLabelToSparseLabel(6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void BadSparseLabel() {
        SparseClassMap map = new SparseClassMap(new int[] { 9, 7, 1, 3, 5, 1, 7, 9 });
        map.sparseLabelToDenseLabel(6);
    }

}
