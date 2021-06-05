/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data.formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Date/time feature extractor.
 *
 * @author Haifeng Li
 */
public class Date implements Term {
    /** The name of variable. */
    private final String name;
    /** The features to extract. */
    private final DateFeature[] features;

    /**
     * Constructor.
     * @param name the name of variable/column.
     * @param features the date/time features to extract.
     */
    public Date(String name, DateFeature... features) {
        this.name = name;
        this.features = features;
    }

    @Override
    public String toString() {
        return String.format("%s%s", name, Arrays.toString(features));
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        int index = schema.indexOf(name);
        DataType type = schema.field(name).type;
        switch (type.id()) {
            case Date:
                if (hasTimeFeatures(features)) {
                    throw new UnsupportedOperationException("Cannot extract time features from a date.");
                }
                break;
            case Time:
                if (hasDateFeatures(features)) {
                    throw new UnsupportedOperationException("Cannot extract date features from a time.");
                }
                break;
            case DateTime:
                // all good
                break;
            default:
                throw new UnsupportedOperationException(String.format("The filed %s is not a date/time: %s", name, type));
        }

        Measure month = new NominalScale(
                new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                new String[] {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"}
                );
        Measure dayOfWeek = new NominalScale(
                new int[] {1, 2, 3, 4, 5, 6, 7},
                new String[] {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}
                );

        List<Feature> features = new ArrayList<>();
        for (DateFeature feature : this.features) {
            features.add(new Feature() {
                final StructField field = new StructField(
                        String.format("%s_%s", name, feature),
                        DataTypes.IntegerType,
                        feature == DateFeature.MONTH ? month : (feature == DateFeature.DAY_OF_WEEK ? dayOfWeek : null));

                @Override
                public String toString() {
                    return field.name;
                }

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public int applyAsInt(Tuple o) {
                    Object x = apply(o);
                    return x == null ? -1 : (int) x;
                }

                @Override
                public Object apply(Tuple o) {
                    Object x = o.get(index);
                    if (x == null) return null;

                    WeekFields weekFields = WeekFields.of(Locale.ROOT);
                    switch (type.id()) {
                        case Date:
                        {
                            LocalDate date = (LocalDate) x;
                            switch (feature) {
                                case YEAR: return date.getYear();
                                case MONTH: return date.getMonthValue();
                                case WEEK_OF_YEAR: return date.get(weekFields.weekOfYear());
                                case WEEK_OF_MONTH: return date.get(weekFields.weekOfMonth());
                                case QUARTER: return date.get(IsoFields.QUARTER_OF_YEAR);
                                case DAY_OF_YEAR: return date.getDayOfYear();
                                case DAY_OF_MONTH: return date.getDayOfMonth();
                                case DAY_OF_WEEK: return date.getDayOfWeek().getValue();
                                default: throw new IllegalStateException("Extract time features from a date.");
                            }
                        }
                        case Time:
                        {
                            LocalTime time = (LocalTime) x;
                            switch (feature) {
                                case HOUR: return time.getHour();
                                case MINUTE: return time.getMinute();
                                case SECOND: return time.getSecond();
                                default: throw new IllegalStateException("Extract date features from a time.");
                            }
                        }
                        case DateTime:
                        {
                            LocalDateTime dateTime = (LocalDateTime) x;
                            switch (feature) {
                                case YEAR: return dateTime.getYear();
                                case MONTH: return dateTime.getMonthValue();
                                case WEEK_OF_YEAR: return dateTime.get(weekFields.weekOfYear());
                                case WEEK_OF_MONTH: return dateTime.get(weekFields.weekOfMonth());
                                case QUARTER: return dateTime.get(IsoFields.QUARTER_OF_YEAR);
                                case DAY_OF_YEAR: return dateTime.getDayOfYear();
                                case DAY_OF_MONTH: return dateTime.getDayOfMonth();
                                case DAY_OF_WEEK: return dateTime.getDayOfWeek().getValue();
                                case HOUR: return dateTime.getHour();
                                case MINUTE: return dateTime.getMinute();
                                case SECOND: return dateTime.getSecond();
                            }
                            break;
                        }
                    }
                    throw new IllegalStateException("Unsupported data type for date/time features");
                }
            });
        }
        return features;
    }

    /** Returns true if there are time related features. */
    private boolean hasTimeFeatures(DateFeature[] features) {
        for (DateFeature feature : features) {
            switch (feature) {
                case HOUR:
                case MINUTE:
                case SECOND: return true;
            }
        }
        return false;
    }

    /** Returns true if there are date related features. */
    private boolean hasDateFeatures(DateFeature[] features) {
        for (DateFeature feature : features) {
            switch (feature) {
                case YEAR:
                case MONTH:
                case WEEK_OF_YEAR:
                case WEEK_OF_MONTH:
                case QUARTER:
                case DAY_OF_YEAR:
                case DAY_OF_MONTH:
                case DAY_OF_WEEK: return true;
            }
        }
        return false;
    }
}
