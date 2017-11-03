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
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;

/**
 * Date/time feature generator. A date attribute is usually represented by a
 * long value that is the milliseconds since January 1, 1970, 00:00:00 GMT.
 * Such a value is meaningless in data mining in general. One usually extract
 * information such as year, month, day, etc. as features in further analysis.
 * 
 * @author Haifeng Li
 */
public class DateFeature implements FeatureGenerator<Date> {
    /**
     * The types of date/time features.
     */
    public enum Type {
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
     * The attributes of generated features.
     */
    private Attribute[] attributes;
    /**
     * The types of features to be generated.
     */
    private Type[] types;

    /**
     * Constructor.
     * @param types the types of features to be generated.
     */
    public DateFeature(Type[] types) {
        this.types = types;
        
        attributes = new Attribute[types.length];
        for (int i = 0; i < attributes.length; i++) {
            switch (types[i]) {
                case MONTH:
                    attributes[i] = new NominalAttribute(types[i].name(), new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"});
                    break;
                case DAY_OF_WEEK:
                    attributes[i] = new NominalAttribute(types[i].name(), new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"});
                    break;
                default:
                    attributes[i] = new NumericAttribute(types[i].name());
            }
        }
    }
    
    @Override
    public Attribute[] attributes() {
        return attributes;
    }

    @Override
    @SuppressWarnings("deprecation")
    public double[] feature(Date date) {
        double[] x = new double[types.length];
        for (int i = 0; i < types.length; i++)
        switch (types[i]) {
            case YEAR:
                x[i] = 1900 + date.getYear();
                break;
            case MONTH:
                x[i] = date.getMonth();
                break;
            case DAY_OF_MONTH:
                x[i] = date.getDate();
                break;
            case DAY_OF_WEEK:
                x[i] = date.getDay();
                break;
            case HOURS:
                x[i] = date.getHours();
                break;
            case MINUTES:
                x[i] = date.getMinutes();
                break;
            case SECONDS:
                x[i] = date.getSeconds();
                break;
            default:
                throw new IllegalStateException("Unknown date feature type: " + types[i]);
        }
        return x;
    }
}
