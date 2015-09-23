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

package smile.data;

import java.sql.Timestamp;

/**
 * An object of generic datum and optional weight. Although the data object
 * is immutable itself, the associated properties such as class label/response
 * weight are mutable.
 *
 * @author Haifeng Li
 */
public class Datum <T> {
    /**
     * Immutable datum object.
     */
    public final T x;
    /**
     * Class label or real-valued response. NaN means unknown label/response.
     */
    public double y = Double.NaN;
    /**
     * Optional weight of this datum. By default, it is 1.0. The particular
     * meaning of weight depends on applications and machine learning algorithms.
     * Although there are on explicit requirements on the weights, in general,
     * they should be positive.
     */
    public double weight = 1.0;
    /**
     * Name of datum.
     */
    public String name;
    /**
     * Optional detailed description.
     */
    public String description;
    /**
     * Timestamp of datum in case of transactional data.
     * Transactional data are time-stamped data collected over time at no
     * particular frequency. Some examples of transactional data are
     * <ul>
     * <li> Internet data </li>
     * <li> Point of Sales (POS) data </li>
     * <li> Inventory data </li>
     * <li> Call Center data </li>
     * <li> Trading data </li>
     * </ul>
     */
    public Timestamp timestamp;

    /**
     * Constructor.
     * @param x the datum.
     */
    public Datum(T x) {
        this.x = x;
    }

    /**
     * Constructor.
     * @param x the datum.
     * @param y the class label or real-valued response.
     */
    public Datum(T x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Constructor.
     * @param x the datum.
     * @param y the class label or real-valued response.
     * @param weight the weight of datum. The particular meaning of weight
     * depends on applications and machine learning algorithms. Although there
     * are on explicit requirements on the weights, in general, they should be
     * positive.
     */
    public Datum(T x, double y, double weight) {
        this.x = x;
        this.y = y;
        this.weight = weight;
    }
}
