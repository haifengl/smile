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

package smile.feature;

import java.util.Date;
import smile.data.Attribute;
import smile.data.DateAttribute;
import smile.data.NumericAttribute;

/**
 * Date/time feature generator. A date attribute is usually represented by a
 * long value that is the milliseconds since January 1, 1970, 00:00:00 GMT.
 * Such a value is meaningless in data mining in general. One usually extract
 * information such as year, month, day, etc. as features in further analysis.
 * 
 * @author Haifeng Li
 */
public class DateFeature implements Feature<double[]> {
    /**
     * The types of date/time features.
     */
    public static enum Type {
        /**
         * The year represented by an integer.
         */
        YEAR,
        /**
         * The month represented by an integer from 0 to 11;
         * 0 is January, 1 is February, and so forth; thus 11 is December.
         */
        MONTH,
        /**
         * The day of month represented by an integer from 1 to 31 in the usual
         * manner.
         */
        DAY_OF_MONTH,
        /**
         * The day of week represented by an integer from 0 to 6;
         * 0 is Sunday, 1 is Monday, and so forth; thus 6 is Saturday.
         */
        DAY_OF_WEEK,
        /**
         * The hours represented by an integer from 0 to 23. Thus, the hour
         * from midnight to 1 a.m. is hour 0, and the hour from noon to 1 p.m.
         * is hour 12.
         */
        HOURS,
        /**
         * The minutes represented by an integer from 0 to 59 in the usual
         * manner.
         */
        MINUTES,
        /**
         * The seconds represented by an integer from 0 to 61; the values
         * 60 and 61 occur only for leap seconds.
         */
        SECONDS,
    }
    
    /**
     * The variable attributes.
     */
    private Attribute[] attributes;
    /**
     * The attributes of generated binary dummy variables.
     */
    private Attribute[] features;
    /**
     * The types of features to be generated.
     */
    private Type[] types;
    /**
     * A map from feature id to original attribute index.
     */
    private int[] map;

    /**
     * Constructor.
     * @param attributes the variable attributes.
     * @param types the types of features to be generated.
     */
    public DateFeature(Attribute[] attributes, Type[] types) {
        this.attributes = attributes;
        this.types = types;
        
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof DateAttribute) {
                p += types.length;
            }
        }
        
        features = new Attribute[p];
        map = new int[p];
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof DateAttribute) {
                DateAttribute date = (DateAttribute) attribute;
                double weight = date.getWeight();
                String name = date.getName();
                String description = date.getDescription();
                
                for (int k = 0; k < types.length; k++, i++) {
                    features[i] = new NumericAttribute(name + "_" + types[k], description, weight);
                    map[i] = j;
                }
            }            
        }
    }
    
    @Override
    public Attribute[] attributes() {
        return features;
    }

    @Override
    @SuppressWarnings("deprecation")
    public double f(double[] object, int id) {
        if (object.length != attributes.length) {
            throw new IllegalArgumentException(String.format("Invalide object size %d, expected %d", object.length, attributes.length));            
        }
        
        if (id < 0 || id >= features.length) {
            throw new IllegalArgumentException("Invalide feature id: " + id);
        }
        
        Date date = new Date(Double.doubleToLongBits(object[map[id]]));
        Type t = types[id % types.length];
        switch (t) {
            case YEAR:
                return 1900 + date.getYear();
            case MONTH:
                return date.getMonth();
            case DAY_OF_MONTH:
                return date.getDate();
            case DAY_OF_WEEK:
                return date.getDay();
            case HOURS:
                return date.getHours();
            case MINUTES:
                return date.getMinutes();
            case SECONDS:
                return date.getSeconds();
        }
        
        throw new IllegalStateException("Impossible to reach here.");
    }    
}
