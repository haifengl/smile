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
package smile.util;

/**
 * Represents a function that produces an float-valued result.
 * This is the float-producing primitive specialization for Function.
 *
 * Java 8 doesn't have ToFloatFunction interface in java.util.function.
 *
 * @author Haifeng Li
 */
/**  */
public interface ToFloatFunction<T> {
    float applyAsFloat(T o);
}

