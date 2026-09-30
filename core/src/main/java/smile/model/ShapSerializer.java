package smile.model;

import java.util.Locale;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Custom Jackson 3 serializer for SHAP explanation values.
 *
 * @author Haifeng Li
 */
public class ShapSerializer extends ValueSerializer<Object> {

    /** Constructor. */
    public ShapSerializer() {
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (value instanceof double[] arr) {
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
        } else if (value instanceof String str) {
            gen.writeString(str);
        } else if (value != null) {
            gen.writeString(value.toString());
        } else {
            gen.writeNull();
        }
    }
}
