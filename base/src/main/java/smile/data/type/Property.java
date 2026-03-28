/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.type;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

/**
 * A component in record or a property in a Java Bean class.
 * @param name the property name.
 * @param type the class type.
 * @param accessor the read accessor.
 * @param field the struct field.
 */
public record Property(String name, Class<?> type, Method accessor, StructField field) {
    /**
     * Returns the property for a record component.
     * @param prop a record component.
     * @return the property for a record component.
     */
    public static Property of(RecordComponent prop) {
        return new Property(prop.getName(), prop.getType(), prop.getAccessor(), StructField.of(prop));
    }

    /**
     * Returns the property for a bean property.
     * @param prop a property descriptor.
     * @return the property for a bean property.
     */
    public static Property of(PropertyDescriptor prop) {
        return new Property(prop.getName(), prop.getPropertyType(), prop.getReadMethod(), StructField.of(prop));
    }

    /**
     * Returns the properties of record or bean class.
     * @param clazz The class type.
     * @return the properties.
     * @throws IntrospectionException if an exception occurs during introspection.
     */
    public static Property[] of(Class<?> clazz) throws IntrospectionException {
        if (clazz.isRecord()) {
            return Arrays.stream(clazz.getRecordComponents())
                    .map(Property::of)
                    .toArray(Property[]::new);
        } else {
            BeanInfo bean = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] props = bean.getPropertyDescriptors();
            return Arrays.stream(props)
                    .filter(prop -> !prop.getName().equals("class"))
                    .map(Property::of)
                    .toArray(Property[]::new);
        }
    }
}
