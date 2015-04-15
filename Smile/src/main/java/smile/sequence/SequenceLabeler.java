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

package smile.sequence;

/**
 * A sequence labeler assigns a class label to each position of the sequence.
 *
 * @author Haifeng Li
 */
public interface SequenceLabeler<T> {
    /**
     * Predicts the sequence labels.
     * @param x a sequence. At each position, it may be the original symbol or
     * a feature set about the symbol, its neighborhood, and/or other information.
     * @return the predicted sequence labels.
     */
    public int[] predict(T[] x);
}
