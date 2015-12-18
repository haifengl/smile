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

package smile.graph;

/**
 * A visitor is encapsulation of some operation on graph vertices during
 * traveling graph (DFS or BFS).
 *
 * @author Haifeng Li
 */
public interface Visitor {
    /**
     * Performs some operations on the currently-visiting vertex during DFS or BFS.
     * @param vertex the index of vertex.
     */
    public void visit(int vertex);
}
