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
package smile.interpolation;

/**
 * In numerical analysis, interpolation is a method of constructing new data
 * points within the range of a discrete set of known data points.
 * In engineering and science one often has a number of data points, as
 * obtained by sampling or experimentation, and tries to construct a function
 * which closely fits those data points. This is called curve fitting or
 * regression analysis. Interpolation is a specific case of curve fitting,
 * in which the function must go exactly through the data points.
 *
 * @author Haifeng Li
 */
public interface Interpolation {

    /**
     * Given a value x, return an interploated value.
     */
    public double interpolate(double x);
}
