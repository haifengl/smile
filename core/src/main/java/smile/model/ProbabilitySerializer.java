package smile.model;

import java.util.Locale;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Custom Jackson 3 serializer for probability values.
 *
 * @author Haifeng Li
 */
public class ProbabilitySerializer extends ValueSerializer<double[]> {

    /** Constructor. */
    public ProbabilitySerializer() {
    }

    @Override
    public void serialize(double[] probabilities, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartArray();
        for (double prob : probabilities) {
            gen.writeRawValue(String.format(Locale.US, "%.3f", prob));
        }
        gen.writeEndArray();
    }
}
