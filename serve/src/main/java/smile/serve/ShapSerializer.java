/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.IOException;
import java.util.Locale;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Custom serializer for SHAP explanation values.
 * Supports string (e.g. {@code "Not supported"}), 1D array (regression),
 * or 2D array (classification {@code [classes][features]}).
 * Formats numeric values to 3 decimal places using {@link Locale#US}.
 *
 * @author Haifeng Li
 */
public class ShapSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String s) {
            gen.writeString(s);
        } else if (value instanceof double[] arr) {
            gen.writeStartArray();
            for (double v : arr) {
                gen.writeRawValue(String.format(Locale.US, "%.3f", v));
            }
            gen.writeEndArray();
        } else if (value instanceof double[][] mat) {
            gen.writeStartArray();
            for (double[] row : mat) {
                gen.writeStartArray();
                for (double v : row) {
                    gen.writeRawValue(String.format(Locale.US, "%.3f", v));
                }
                gen.writeEndArray();
            }
            gen.writeEndArray();
        } else {
            serializers.defaultSerializeValue(value, gen);
        }
    }
}
