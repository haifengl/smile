/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.type;

import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.avro.Schema;
import org.apache.parquet.column.ColumnDescriptor;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;

import static org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE;
import static org.apache.arrow.vector.types.FloatingPointPrecision.SINGLE;

/**
 * A field in a Struct data type.
 *
 * @author Haifeng Li
 */
public record StructField(String name, DataType dtype, Measure measure) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Constructor.
     * @param name the field name.
     * @param dtype the field data type.
     * @param measure the level of measurement.
     */
    public StructField {
        if (measure instanceof NumericalMeasure && !dtype.isFloating()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", dtype, measure));
        }

        if (measure instanceof CategoricalMeasure && !dtype.isIntegral()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", dtype, measure));
        }
    }

    /**
     * Constructor.
     * @param name the field name.
     * @param dtype the field data type.
     */
    public StructField(String name, DataType dtype) {
        this(name, dtype, null);
    }

    @Override
    public String toString() {
        return measure != null ? String.format("%s: %s %s", name, dtype, measure) : String.format("%s: %s", name, dtype);
    }

    /**
     * Returns the string representation of the field object.
     * @param o the object.
     * @return the string representation.
     */
    public String toString(Object o) {
        if (o == null) return "null";
        return measure != null ? measure.toString(o) : dtype.toString(o);
    }

    /**
     * Returns the object value of string.
     * @param s the string.
     * @return the object value.
     */
    public Object valueOf(String s) {
        return measure != null ? measure.valueOf(s) : dtype.valueOf(s);
    }

    /**
     * Returns true if the field is of integer or floating but not nominal scale.
     * @return true if the field is of integer or floating but not nominal scale.
     */
    public boolean isNumeric() {
        if (measure instanceof NominalScale) {
            return false;
        }

        return dtype.isFloating() || dtype.isIntegral();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructField f) {
            return name.equals(f.name) && dtype.equals(f.dtype) && Objects.equals(measure, f.measure);
        }

        return false;
    }

    /**
     * Returns the struct field of a class property.
     * @param prop the property descriptor.
     * @return the struct field.
     */
    public static StructField of(PropertyDescriptor prop) {
        Class<?> clazz = prop.getPropertyType();
        DataType dtype = DataType.of(clazz);
        NominalScale scale = getScale(clazz);
        return new StructField(prop.getName(), dtype, scale);
    }

    /**
     * Returns the struct field of a record component.
     * @param comp the record component.
     * @return the struct field.
     */
    public static StructField of(RecordComponent comp) {
        Class<?> clazz = comp.getType();
        DataType dtype = DataType.of(clazz);
        NominalScale scale = getScale(clazz);
        return new StructField(comp.getName(), dtype, scale);
    }

    /**
     * Returns the nominal scale of an enum class.
     * @param clazz an enum class.
     * @return the nominal scale or null if clazz is not an enum.
     */
    private static NominalScale getScale(Class<?> clazz) {
        if (clazz.isEnum()) {
            Object[] levels = clazz.getEnumConstants();
            return new NominalScale(Arrays.stream(levels).map(Object::toString).toArray(String[]::new));
        }
        return null;
    }

    /**
     * Converts an avro schema field to smile field.
     * @param field an avro schema field.
     * @return the struct field.
     */
    public static StructField of(Schema.Field field) {
        NominalScale scale = null;
        if (field.schema().getType() == Schema.Type.ENUM) {
            scale = new NominalScale(field.schema().getEnumSymbols());
        }

        return new StructField(field.name(), DataType.of(field.schema()), scale);
    }

    /**
     * Converts a parquet column to smile field.
     * @param column a parquet column descriptor.
     * @return the struct field.
     */
    public static StructField of(ColumnDescriptor column) {
        String name = String.join(".", column.getPath());
        DataType dtype = DataType.of(column.getPrimitiveType());
        return new StructField(name, dtype);
    }

    /**
     * Converts an arrow field column to smile field.
     * @param field an arrow field.
     * @return the struct field.
     */
    public static StructField of(Field field) {
        String name = field.getName();
        ArrowType type = field.getType();
        boolean nullable = field.isNullable();
        var dtype = switch (type.getTypeID()) {
            case Int -> {
                ArrowType.Int itype = (ArrowType.Int) type;
                int bitWidth = itype.getBitWidth();
                yield switch (bitWidth) {
                    case 8 -> DataTypes.ByteType;
                    case 16 -> DataTypes.ShortType;
                    case 32 -> DataTypes.IntegerType;
                    case 64 -> DataTypes.LongType;
                    default -> throw new UnsupportedOperationException("Unsupported integer bit width: " + bitWidth);
                };
            }

            case FloatingPoint -> {
                FloatingPointPrecision precision = ((ArrowType.FloatingPoint) type).getPrecision();
                yield switch (precision) {
                    case DOUBLE -> DataTypes.DoubleType;
                    case SINGLE -> DataTypes.FloatType;
                    case HALF -> throw new UnsupportedOperationException("Unsupported float precision: " + precision);
                };
            }

            case Bool -> DataTypes.BooleanType;
            case Decimal -> DataTypes.DecimalType;
            case Utf8 -> DataTypes.StringType;
            case Date -> DataTypes.DateType;
            case Time -> DataTypes.TimeType;
            case Timestamp -> DataTypes.DateTimeType;
            case Binary, FixedSizeBinary -> DataTypes.ByteArrayType;
            case List, FixedSizeList -> {
                List<Field> child = field.getChildren();
                if (child.size() != 1) {
                    throw new IllegalStateException(String.format("List type has %d child fields.", child.size()));
                }

                yield DataTypes.array(StructField.of(child.getFirst()).dtype());
            }

            case Struct -> {
                List<StructField> children = field.getChildren().stream().map(StructField::of).toList();
                yield new StructType(children);
            }

            default ->
                throw new UnsupportedOperationException("Unsupported arrow to smile type conversion: " + type);
        };
        return new StructField(name, dtype);
    }

    /**
     * Converts a smile struct field to arrow field.
     * @return the arrow field.
     */
    public Field toArrow() {
        return switch (dtype.id()) {
            case Integer -> new Field(name, new FieldType(false, new ArrowType.Int(32, true), null), null);
            case Long -> new Field(name, new FieldType(false, new ArrowType.Int(64, true), null), null);
            case Double -> new Field(name, new FieldType(false, new ArrowType.FloatingPoint(DOUBLE), null), null);
            case Float -> new Field(name, new FieldType(false, new ArrowType.FloatingPoint(SINGLE), null), null);
            case Boolean -> new Field(name, new FieldType(false, new ArrowType.Bool(), null), null);
            case Byte -> new Field(name, new FieldType(false, new ArrowType.Int(8, true), null), null);
            case Short -> new Field(name, new FieldType(false, new ArrowType.Int(16, true), null), null);
            case Char -> new Field(name, new FieldType(false, new ArrowType.Int(16, false), null), null);
            case Decimal -> new Field(name, FieldType.nullable(new ArrowType.Decimal(28, 10, 128)), null);
            case String -> new Field(name, FieldType.nullable(new ArrowType.Utf8()), null);
            case Date -> new Field(name, FieldType.nullable(new ArrowType.Date(DateUnit.DAY)), null);
            case Time -> new Field(name, FieldType.nullable(new ArrowType.Time(TimeUnit.MILLISECOND, 32)), null);
            case DateTime -> new Field(name, FieldType.nullable(new ArrowType.Timestamp(TimeUnit.MILLISECOND, java.time.ZoneOffset.UTC.getId())), null);
            case Object -> {
                Class<?> clazz = ((ObjectType) dtype).getObjectClass();
                if (clazz == Integer.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(32, true)), null);
                } else if (clazz == Long.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(64, true)), null);
                } else if (clazz == Double.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(DOUBLE)), null);
                } else if (clazz == Float.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(SINGLE)), null);
                } else if (clazz == Boolean.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Bool()), null);
                } else if (clazz == Byte.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(8, true)), null);
                } else if (clazz == Short.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(16, true)), null);
                } else if (clazz == Character.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(16, false)), null);
                } else {
                    throw new UnsupportedOperationException("Unsupported arrow type conversion: " + clazz.getName());
                }
            }
            case Array -> {
                DataType etype = ((ArrayType) dtype).getComponentType();
                yield switch (etype.id()) {
                    case Integer -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(32, true), null), null))
                        );
                    case Long -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(64, true), null), null))
                        );
                    case Double -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(DOUBLE), null), null))
                        );
                    case Float -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(SINGLE), null), null))
                        );
                    case Boolean -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Bool(), null), null))
                        );
                    case Byte -> new Field(name, FieldType.nullable(new ArrowType.Binary()), null);
                    case Short -> new Field(name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(16, true), null), null))
                        );
                    case Char -> new Field(name, FieldType.nullable(new ArrowType.Utf8()), null);
                    default -> throw new UnsupportedOperationException("Unsupported array type conversion: " + etype);
                };
            }
            case Struct -> {
                StructType children = (StructType) dtype;
                yield new Field(name,
                        new FieldType(false, new ArrowType.Struct(), null),
                        // children type
                        Arrays.stream(children.fields()).map(StructField::toArrow).toList()
                );
            }
        };
    }
}
