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
package smile.data.formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * Lift a const value to a factor.
 *
 * @author Haifeng Li
 */
public class Const {
    /**
     * Utility class should not have public constructors.
     */
    private Const() {
    }

    /** Returns a const boolean factor. */
    public Factor of(final boolean x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.BooleanType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const char factor. */
    public Factor of(final char x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.CharType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const byte factor. */
    public Factor of(final byte x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.ByteType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const short factor. */
    public Factor of(final short x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.ShortType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const integer factor. */
    public Factor of(final int x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.IntegerType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const long factor. */
    public Factor of(final long x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%d)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.LongType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const float factor. */
    public Factor of(final float x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%G)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.FloatType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public float applyAsFloat(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const double factor. */
    public Factor of(final double x) {
        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%G)", x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.DoubleType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const integer factor. */
    public Factor of(final Object x) {
        final DataType type = x instanceof String ? DataTypes.StringType :
                x instanceof LocalDate ? DataTypes.DateType :
                x instanceof LocalDateTime ? DataTypes.DateTimeType :
                DataType.of(x.getClass());

        return new Factor() {
            @Override
            public String name() {
                return String.format("const(%s)", x);
            }

            @Override
            public String toString() {
                return x.toString();
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Factor> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return type;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }
}
