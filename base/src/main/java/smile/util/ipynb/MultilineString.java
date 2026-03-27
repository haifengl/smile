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
package smile.util.ipynb;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * A notebook multiline string value. The nbformat specification allows a field
 * such as {@code source} or output {@code text} to be stored either as a single
 * string or as an array of strings (each line retaining its trailing newline).
 * This wrapper transparently handles both forms during (de)serialization and
 * exposes the content as a single concatenated {@link String}.
 * <p>
 * Serialization always produces an array of strings (one entry per line),
 * matching the canonical nbformat 4 representation.
 *
 * @param lines the individual lines that make up the string, each line
 *              retaining its trailing {@code \n} except possibly the last one.
 *
 * @author Haifeng Li
 */
@JsonDeserialize(using = MultilineString.Deserializer.class)
@JsonSerialize(using = MultilineString.Serializer.class)
public record MultilineString(List<String> lines) {

    /**
     * Returns the full content as a single concatenated string.
     * @return the joined string.
     */
    public String value() {
        return String.join("", lines);
    }

    /**
     * Creates a {@code MultilineString} from a plain string by splitting on
     * newline boundaries while keeping the delimiter at the end of each line.
     *
     * @param text the source text.
     * @return a new {@code MultilineString}.
     */
    public static MultilineString of(String text) {
        if (text == null || text.isEmpty()) {
            return new MultilineString(List.of());
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return new MultilineString(lines);
    }

    // -----------------------------------------------------------------------
    // Jackson (de)serialization
    // -----------------------------------------------------------------------

    /** Deserializes either a JSON string or a JSON array of strings. */
    static class Deserializer extends StdDeserializer<MultilineString> {
        Deserializer() {
            super(MultilineString.class);
        }

        @Override
        public MultilineString deserialize(JsonParser p, DeserializationContext ctx)
                throws JacksonException {
            if (p.currentToken() == JsonToken.START_ARRAY) {
                List<String> lines = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    lines.add(p.getString());
                }
                return new MultilineString(lines);
            } else {
                // single string
                return MultilineString.of(p.getString());
            }
        }
    }

    /** Serializes as a JSON array of strings. */
    static class Serializer extends StdSerializer<MultilineString> {
        Serializer() {
            super(MultilineString.class);
        }

        @Override
        public void serialize(MultilineString value, JsonGenerator gen, SerializationContext provider) {
            gen.writeStartArray();
            for (String line : value.lines()) {
                gen.writeString(line);
            }
            gen.writeEndArray();
        }
    }
}

